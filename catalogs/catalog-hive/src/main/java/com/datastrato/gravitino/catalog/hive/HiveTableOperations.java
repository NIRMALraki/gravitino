/*
 * Copyright 2024 Datastrato Pvt Ltd.
 * This software is licensed under the Apache License version 2.
 */
package com.datastrato.gravitino.catalog.hive;

import com.datastrato.gravitino.catalog.TableOperations;
import com.datastrato.gravitino.exceptions.NoSuchPartitionException;
import com.datastrato.gravitino.exceptions.NoSuchTableException;
import com.datastrato.gravitino.exceptions.PartitionAlreadyExistsException;
import com.datastrato.gravitino.rel.SupportsPartitions;
import com.datastrato.gravitino.rel.expressions.literals.Literal;
import com.datastrato.gravitino.rel.expressions.literals.Literals;
import com.datastrato.gravitino.rel.expressions.transforms.Transforms;
import com.datastrato.gravitino.rel.partitions.IdentityPartition;
import com.datastrato.gravitino.rel.partitions.Partition;
import com.datastrato.gravitino.rel.partitions.Partitions;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.hadoop.hive.metastore.api.NoSuchObjectException;
import org.apache.hadoop.hive.metastore.api.SerDeInfo;
import org.apache.hadoop.hive.metastore.api.StorageDescriptor;
import org.apache.hadoop.hive.metastore.api.UnknownTableException;
import org.apache.parquet.Strings;
import org.apache.thrift.TException;

public class HiveTableOperations implements TableOperations, SupportsPartitions {

  private static final String PARTITION_NAME_DELIMITER = "/";
  private static final String PARTITION_VALUE_DELIMITER = "=";

  private final HiveTable table;

  public HiveTableOperations(HiveTable table) {
    Preconditions.checkArgument(table != null, "table must not be null");
    this.table = table;
  }

  @Override
  public String[] listPartitionNames() {
    try {
      return table
          .clientPool()
          .run(
              c ->
                  c.listPartitionNames(table.schemaName(), table.name(), (short) -1)
                      .toArray(new String[0]));
    } catch (TException | InterruptedException e) {
      throw new RuntimeException(
          "Failed to list partition names of table " + table.name() + "from Hive Metastore", e);
    }
  }

  @Override
  public Partition[] listPartitions() {
    List<String> partitionNames;
    List<org.apache.hadoop.hive.metastore.api.Partition> partitions;
    try {
      partitionNames =
          table
              .clientPool()
              .run(c -> c.listPartitionNames(table.schemaName(), table.name(), (short) -1));
      partitions =
          table
              .clientPool()
              .run(c -> c.getPartitionsByNames(table.schemaName(), table.name(), partitionNames));
    } catch (TException | InterruptedException e) {
      throw new RuntimeException(e);
    }

    // should never happen
    Preconditions.checkArgument(
        partitionNames.size() == partitions.size(),
        "oops?! partition names and partitions size are not equal: %s vs %s",
        partitionNames.size(),
        partitions.size());

    return IntStream.range(0, partitionNames.size())
        .mapToObj(i -> fromHivePartition(partitionNames.get(i), partitions.get(i)))
        .toArray(Partition[]::new);
  }

  @Override
  public Partition getPartition(String partitionName) throws NoSuchPartitionException {
    try {
      org.apache.hadoop.hive.metastore.api.Partition partition =
          table
              .clientPool()
              .run(c -> c.getPartition(table.schemaName(), table.name(), partitionName));
      return fromHivePartition(partitionName, partition);

    } catch (UnknownTableException e) {
      throw new NoSuchTableException(
          "Hive table " + table.name() + " does not exist in Hive Metastore", e);

    } catch (NoSuchObjectException e) {
      throw new NoSuchPartitionException(
          "Hive partition " + partitionName + " does not exist in Hive Metastore", e);

    } catch (TException | InterruptedException e) {
      throw new RuntimeException(
          "Failed to get partition "
              + partitionName
              + " of table "
              + table.name()
              + "from Hive Metastore",
          e);
    }
  }

  private Partition fromHivePartition(
      String partitionName, org.apache.hadoop.hive.metastore.api.Partition partition) {
    String[][] fieldNames = getFieldNames(partitionName);
    Literal[] values =
        partition.getValues().stream().map(Literals::stringLiteral).toArray(Literal[]::new);
    // todo: support partition properties metadata to get more necessary information
    return Partitions.identity(partitionName, fieldNames, values, partition.getParameters());
  }

  private String[][] getFieldNames(String partitionName) {
    // Hive partition name is in the format of "field1=value1/field2=value2/..."
    String[] fields = partitionName.split(PARTITION_NAME_DELIMITER);
    return Arrays.stream(fields)
        .map(field -> new String[] {field.split(PARTITION_VALUE_DELIMITER)[0]})
        .toArray(String[][]::new);
  }

  @Override
  public Partition addPartition(Partition partition) throws PartitionAlreadyExistsException {
    Preconditions.checkArgument(
        partition instanceof IdentityPartition, "Hive only supports identity partition");
    IdentityPartition identityPartition = (IdentityPartition) partition;

    Set<String> transformFields =
        Arrays.stream(table.partitioning())
            .map(t -> ((Transforms.IdentityTransform) t).fieldName()[0])
            .collect(Collectors.toSet());

    Preconditions.checkArgument(
        transformFields.size() == identityPartition.fieldNames().length,
        "Hive partition field names must be the same as table partitioning field names: %s, but got %s",
        Strings.join(transformFields, ","),
        Strings.join(
            Arrays.stream(identityPartition.fieldNames())
                .map(f -> Strings.join(f, "."))
                .collect(Collectors.toList()),
            ","));
    Arrays.stream(identityPartition.fieldNames())
        .forEach(
            f ->
                Preconditions.checkArgument(
                    transformFields.contains(f[0]),
                    "Hive partition field name must be in table partitioning field names: %s, but got %s",
                    Strings.join(transformFields, ","),
                    f[0]));

    try {
      org.apache.hadoop.hive.metastore.api.Partition createdPartition =
          table.clientPool().run(c -> c.add_partition(toHivePartition(identityPartition)));
      return fromHivePartition(
          generatePartitionName((IdentityPartition) partition), createdPartition);
    } catch (TException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private String generatePartitionName(IdentityPartition partition) {
    Arrays.stream(partition.fieldNames())
        .forEach(
            fieldName ->
                Preconditions.checkArgument(
                    fieldName.length == 1,
                    "Hive catalog does not support nested partition field names"));

    // Hive partition name is in the format of "field1=value1/field2=value2/..."
    return IntStream.range(0, partition.fieldNames().length)
        .mapToObj(
            i ->
                partition.fieldNames()[i][0]
                    + PARTITION_VALUE_DELIMITER
                    + partition.values()[i].value().toString())
        .collect(Collectors.joining(PARTITION_NAME_DELIMITER));
  }

  private org.apache.hadoop.hive.metastore.api.Partition toHivePartition(
      IdentityPartition partition) {
    org.apache.hadoop.hive.metastore.api.Partition hivePartition =
        new org.apache.hadoop.hive.metastore.api.Partition();
    hivePartition.setDbName(table.schemaName());
    hivePartition.setTableName(table.name());

    // todo: support custom serde and location if necessary
    StorageDescriptor sd;
    if (table.storageDescriptor() == null) {
      // In theory, this should not happen because the Hive table will reload after creating
      // in CatalogOperationDispatcher and the storage descriptor will be set. But in case of the
      // Hive table is created by other ways(such as UT), we need to handle this.
      sd = new StorageDescriptor();
      sd.setSerdeInfo(new SerDeInfo());
    } else {
      sd = table.storageDescriptor().deepCopy();
      // The location will be automatically generated by Hive Metastore
      sd.setLocation(null);
    }
    hivePartition.setSd(sd);

    hivePartition.setParameters(partition.properties());

    hivePartition.setValues(
        Arrays.stream(partition.values())
            .map(l -> l.value().toString())
            .collect(Collectors.toList()));

    return hivePartition;
  }

  @Override
  public boolean dropPartition(String partitionName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void close() throws IOException {
    table.close();
  }
}
