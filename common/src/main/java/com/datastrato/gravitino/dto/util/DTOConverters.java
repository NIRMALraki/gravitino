/*
 * Copyright 2023 Datastrato Pvt Ltd.
 * This software is licensed under the Apache License version 2.
 */
package com.datastrato.gravitino.dto.util;

import static com.datastrato.gravitino.rel.expressions.transforms.Transforms.NAME_OF_IDENTITY;

import com.datastrato.gravitino.Audit;
import com.datastrato.gravitino.Catalog;
import com.datastrato.gravitino.Metalake;
import com.datastrato.gravitino.dto.AuditDTO;
import com.datastrato.gravitino.dto.CatalogDTO;
import com.datastrato.gravitino.dto.MetalakeDTO;
import com.datastrato.gravitino.dto.rel.ColumnDTO;
import com.datastrato.gravitino.dto.rel.DistributionDTO;
import com.datastrato.gravitino.dto.rel.DistributionDTO.Builder;
import com.datastrato.gravitino.dto.rel.SchemaDTO;
import com.datastrato.gravitino.dto.rel.SortOrderDTO;
import com.datastrato.gravitino.dto.rel.TableDTO;
import com.datastrato.gravitino.dto.rel.expressions.FieldReferenceDTO;
import com.datastrato.gravitino.dto.rel.expressions.FuncExpressionDTO;
import com.datastrato.gravitino.dto.rel.expressions.FunctionArg;
import com.datastrato.gravitino.dto.rel.expressions.LiteralDTO;
import com.datastrato.gravitino.dto.rel.expressions.UnparsedExpressionDTO;
import com.datastrato.gravitino.dto.rel.indexes.IndexDTO;
import com.datastrato.gravitino.dto.rel.partitioning.BucketPartitioningDTO;
import com.datastrato.gravitino.dto.rel.partitioning.DayPartitioningDTO;
import com.datastrato.gravitino.dto.rel.partitioning.FunctionPartitioningDTO;
import com.datastrato.gravitino.dto.rel.partitioning.HourPartitioningDTO;
import com.datastrato.gravitino.dto.rel.partitioning.IdentityPartitioningDTO;
import com.datastrato.gravitino.dto.rel.partitioning.ListPartitioningDTO;
import com.datastrato.gravitino.dto.rel.partitioning.MonthPartitioningDTO;
import com.datastrato.gravitino.dto.rel.partitioning.Partitioning;
import com.datastrato.gravitino.dto.rel.partitioning.RangePartitioningDTO;
import com.datastrato.gravitino.dto.rel.partitioning.TruncatePartitioningDTO;
import com.datastrato.gravitino.dto.rel.partitioning.YearPartitioningDTO;
import com.datastrato.gravitino.dto.rel.partitions.IdentityPartitionDTO;
import com.datastrato.gravitino.dto.rel.partitions.ListPartitionDTO;
import com.datastrato.gravitino.dto.rel.partitions.PartitionDTO;
import com.datastrato.gravitino.dto.rel.partitions.RangePartitionDTO;
import com.datastrato.gravitino.rel.Column;
import com.datastrato.gravitino.rel.Schema;
import com.datastrato.gravitino.rel.Table;
import com.datastrato.gravitino.rel.expressions.Expression;
import com.datastrato.gravitino.rel.expressions.FunctionExpression;
import com.datastrato.gravitino.rel.expressions.NamedReference;
import com.datastrato.gravitino.rel.expressions.UnparsedExpression;
import com.datastrato.gravitino.rel.expressions.distributions.Distribution;
import com.datastrato.gravitino.rel.expressions.distributions.Distributions;
import com.datastrato.gravitino.rel.expressions.literals.Literal;
import com.datastrato.gravitino.rel.expressions.literals.Literals;
import com.datastrato.gravitino.rel.expressions.sorts.SortOrder;
import com.datastrato.gravitino.rel.expressions.sorts.SortOrders;
import com.datastrato.gravitino.rel.expressions.transforms.Transform;
import com.datastrato.gravitino.rel.expressions.transforms.Transforms;
import com.datastrato.gravitino.rel.indexes.Index;
import com.datastrato.gravitino.rel.indexes.Indexes;
import com.datastrato.gravitino.rel.partitions.IdentityPartition;
import com.datastrato.gravitino.rel.partitions.ListPartition;
import com.datastrato.gravitino.rel.partitions.Partition;
import com.datastrato.gravitino.rel.partitions.Partitions;
import com.datastrato.gravitino.rel.partitions.RangePartition;
import com.datastrato.gravitino.rel.types.Types;
import java.util.Arrays;
import org.apache.commons.lang3.ArrayUtils;

public class DTOConverters {

  private DTOConverters() {}

  public static AuditDTO toDTO(Audit audit) {
    return AuditDTO.builder()
        .withCreator(audit.creator())
        .withCreateTime(audit.createTime())
        .withLastModifier(audit.lastModifier())
        .withLastModifiedTime(audit.lastModifiedTime())
        .build();
  }

  public static MetalakeDTO toDTO(Metalake metalake) {
    return new MetalakeDTO.Builder()
        .withName(metalake.name())
        .withComment(metalake.comment())
        .withProperties(metalake.properties())
        .withAudit(toDTO(metalake.auditInfo()))
        .build();
  }

  public static PartitionDTO toDTO(Partition partition) {
    if (partition instanceof RangePartition) {
      RangePartition rangePartition = (RangePartition) partition;
      return RangePartitionDTO.builder()
          .withName(rangePartition.name())
          .withUpper((LiteralDTO) toFunctionArg(rangePartition.upper()))
          .withLower((LiteralDTO) toFunctionArg(rangePartition.lower()))
          .withProperties(rangePartition.properties())
          .build();
    } else if (partition instanceof IdentityPartition) {
      IdentityPartition identityPartition = (IdentityPartition) partition;
      return IdentityPartitionDTO.builder()
          .withName(identityPartition.name())
          .withFieldNames(identityPartition.fieldNames())
          .withValues(
              Arrays.stream(identityPartition.values())
                  .map(v -> (LiteralDTO) toFunctionArg(v))
                  .toArray(LiteralDTO[]::new))
          .withProperties(identityPartition.properties())
          .build();
    } else if (partition instanceof ListPartition) {
      ListPartition listPartition = (ListPartition) partition;
      return ListPartitionDTO.builder()
          .withName(listPartition.name())
          .withLists(
              Arrays.stream(listPartition.lists())
                  .map(
                      list ->
                          Arrays.stream(list)
                              .map(v -> (LiteralDTO) toFunctionArg(v))
                              .toArray(LiteralDTO[]::new))
                  .toArray(LiteralDTO[][]::new))
          .withProperties(listPartition.properties())
          .build();
    } else {
      throw new IllegalArgumentException("Unsupported partition type: " + partition.getClass());
    }
  }

  public static CatalogDTO toDTO(Catalog catalog) {
    return new CatalogDTO.Builder()
        .withName(catalog.name())
        .withType(catalog.type())
        .withProvider(catalog.provider())
        .withComment(catalog.comment())
        .withProperties(catalog.properties())
        .withAudit(toDTO(catalog.auditInfo()))
        .build();
  }

  public static SchemaDTO toDTO(Schema schema) {
    return new SchemaDTO.Builder()
        .withName(schema.name())
        .withComment(schema.comment())
        .withProperties(schema.properties())
        .withAudit(toDTO(schema.auditInfo()))
        .build();
  }

  public static ColumnDTO toDTO(Column column) {
    return new ColumnDTO.Builder()
        .withName(column.name())
        .withDataType(column.dataType())
        .withComment(column.comment())
        .withNullable(column.nullable())
        .withAutoIncrement(column.autoIncrement())
        .withDefaultValue(
            (column.defaultValue() == null
                    || column.defaultValue().equals(Column.DEFAULT_VALUE_NOT_SET))
                ? Column.DEFAULT_VALUE_NOT_SET
                : toFunctionArg(column.defaultValue()))
        .build();
  }

  public static TableDTO toDTO(Table table) {
    return new TableDTO.Builder()
        .withName(table.name())
        .withComment(table.comment())
        .withColumns(
            Arrays.stream(table.columns()).map(DTOConverters::toDTO).toArray(ColumnDTO[]::new))
        .withProperties(table.properties())
        .withSortOrders(toDTOs(table.sortOrder()))
        .withDistribution(toDTO(table.distribution()))
        .withAudit(toDTO(table.auditInfo()))
        .withPartitioning(toDTOs(table.partitioning()))
        .withIndex(toDTOs(table.index()))
        .build();
  }

  public static DistributionDTO toDTO(Distribution distribution) {
    if (Distributions.NONE.equals(distribution) || null == distribution) {
      return DistributionDTO.NONE;
    }

    if (distribution instanceof DistributionDTO) {
      return (DistributionDTO) distribution;
    }

    return new Builder()
        .withStrategy(distribution.strategy())
        .withNumber(distribution.number())
        .withArgs(
            Arrays.stream(distribution.expressions())
                .map(DTOConverters::toFunctionArg)
                .toArray(FunctionArg[]::new))
        .build();
  }

  public static SortOrderDTO toDTO(SortOrder sortOrder) {
    if (sortOrder instanceof SortOrderDTO) {
      return (SortOrderDTO) sortOrder;
    }
    return new SortOrderDTO.Builder()
        .withSortTerm(toFunctionArg(sortOrder.expression()))
        .withDirection(sortOrder.direction())
        .withNullOrder(sortOrder.nullOrdering())
        .build();
  }

  public static Partitioning toDTO(Transform transform) {
    if (transform instanceof Partitioning) {
      return (Partitioning) transform;
    }

    if (transform instanceof Transform.SingleFieldTransform) {
      String[] fieldName = ((Transform.SingleFieldTransform) transform).fieldName();
      switch (transform.name()) {
        case NAME_OF_IDENTITY:
          return IdentityPartitioningDTO.of(fieldName);
        case Transforms.NAME_OF_YEAR:
          return YearPartitioningDTO.of(fieldName);
        case Transforms.NAME_OF_MONTH:
          return MonthPartitioningDTO.of(fieldName);
        case Transforms.NAME_OF_DAY:
          return DayPartitioningDTO.of(fieldName);
        case Transforms.NAME_OF_HOUR:
          return HourPartitioningDTO.of(fieldName);
        default:
          throw new IllegalArgumentException("Unsupported transform: " + transform.name());
      }
    } else if (transform instanceof Transforms.BucketTransform) {
      return BucketPartitioningDTO.of(
          ((Transforms.BucketTransform) transform).numBuckets(),
          ((Transforms.BucketTransform) transform).fieldNames());
    } else if (transform instanceof Transforms.TruncateTransform) {
      return TruncatePartitioningDTO.of(
          ((Transforms.TruncateTransform) transform).width(),
          ((Transforms.TruncateTransform) transform).fieldName());
    } else if (transform instanceof Transforms.ListTransform) {
      return ListPartitioningDTO.of(((Transforms.ListTransform) transform).fieldNames());
    } else if (transform instanceof Transforms.RangeTransform) {
      return RangePartitioningDTO.of(((Transforms.RangeTransform) transform).fieldName());
    } else if (transform instanceof Transforms.ApplyTransform) {
      return FunctionPartitioningDTO.of(transform.name(), toFunctionArg(transform.arguments()));
    } else {
      throw new IllegalArgumentException("Unsupported transform: " + transform.name());
    }
  }

  public static IndexDTO toDTO(Index index) {
    if (index instanceof IndexDTO) {
      return (IndexDTO) index;
    }
    return IndexDTO.builder()
        .withIndexType(index.type())
        .withName(index.name())
        .withFieldNames(index.fieldNames())
        .build();
  }

  public static FunctionArg toFunctionArg(Expression expression) {
    if (expression instanceof FunctionArg) {
      return (FunctionArg) expression;
    }

    if (expression instanceof Literal) {
      if (Literals.NULL.equals(expression)) {
        return LiteralDTO.NULL;
      }
      return new LiteralDTO.Builder()
          .withValue((((Literal) expression).value().toString()))
          .withDataType(((Literal) expression).dataType())
          .build();
    } else if (expression instanceof NamedReference.FieldReference) {
      return new FieldReferenceDTO.Builder()
          .withFieldName(((NamedReference.FieldReference) expression).fieldName())
          .build();
    } else if (expression instanceof FunctionExpression) {
      return new FuncExpressionDTO.Builder()
          .withFunctionName(((FunctionExpression) expression).functionName())
          .withFunctionArgs(
              Arrays.stream(((FunctionExpression) expression).arguments())
                  .map(DTOConverters::toFunctionArg)
                  .toArray(FunctionArg[]::new))
          .build();
    } else if (expression instanceof UnparsedExpression) {
      return UnparsedExpressionDTO.builder()
          .withUnparsedExpression(((UnparsedExpression) expression).unparsedExpression())
          .build();
    } else {
      throw new IllegalArgumentException("Unsupported expression type: " + expression.getClass());
    }
  }

  public static FunctionArg[] toFunctionArg(Expression[] expressions) {
    if (ArrayUtils.isEmpty(expressions)) {
      return FunctionArg.EMPTY_ARGS;
    }
    return Arrays.stream(expressions).map(DTOConverters::toFunctionArg).toArray(FunctionArg[]::new);
  }

  public static ColumnDTO[] toDTOs(Column[] columns) {
    if (ArrayUtils.isEmpty(columns)) {
      return new ColumnDTO[0];
    }
    return Arrays.stream(columns).map(DTOConverters::toDTO).toArray(ColumnDTO[]::new);
  }

  public static SortOrderDTO[] toDTOs(SortOrder[] sortOrders) {
    if (ArrayUtils.isEmpty(sortOrders)) {
      return new SortOrderDTO[0];
    }
    return Arrays.stream(sortOrders).map(DTOConverters::toDTO).toArray(SortOrderDTO[]::new);
  }

  public static Partitioning[] toDTOs(Transform[] transforms) {
    if (ArrayUtils.isEmpty(transforms)) {
      return new Partitioning[0];
    }
    return Arrays.stream(transforms).map(DTOConverters::toDTO).toArray(Partitioning[]::new);
  }

  public static IndexDTO[] toDTOs(Index[] indexes) {
    if (ArrayUtils.isEmpty(indexes)) {
      return IndexDTO.EMPTY_INDEXES;
    }
    return Arrays.stream(indexes).map(DTOConverters::toDTO).toArray(IndexDTO[]::new);
  }

  public static PartitionDTO[] toDTOs(Partition[] partitions) {
    if (ArrayUtils.isEmpty(partitions)) {
      return new PartitionDTO[0];
    }
    return Arrays.stream(partitions).map(DTOConverters::toDTO).toArray(PartitionDTO[]::new);
  }

  public static Distribution fromDTO(DistributionDTO distributionDTO) {
    if (DistributionDTO.NONE.equals(distributionDTO) || null == distributionDTO) {
      return Distributions.NONE;
    }

    return Distributions.of(
        distributionDTO.strategy(),
        distributionDTO.number(),
        fromFunctionArgs(distributionDTO.args()));
  }

  public static Expression[] fromFunctionArgs(FunctionArg[] args) {
    if (ArrayUtils.isEmpty(args)) {
      return Expression.EMPTY_EXPRESSION;
    }
    return Arrays.stream(args).map(DTOConverters::fromFunctionArg).toArray(Expression[]::new);
  }

  public static Expression fromFunctionArg(FunctionArg arg) {
    switch (arg.argType()) {
      case LITERAL:
        if (((LiteralDTO) arg).value() == null
            || ((LiteralDTO) arg).dataType().equals(Types.NullType.get())) {
          return Literals.NULL;
        }
        return Literals.of(((LiteralDTO) arg).value(), ((LiteralDTO) arg).dataType());
      case FIELD:
        return NamedReference.field(((FieldReferenceDTO) arg).fieldName());
      case FUNCTION:
        return FunctionExpression.of(
            ((FuncExpressionDTO) arg).functionName(),
            fromFunctionArgs(((FuncExpressionDTO) arg).args()));
      default:
        throw new IllegalArgumentException("Unsupported expression type: " + arg.getClass());
    }
  }

  public static Index fromDTO(IndexDTO indexDTO) {
    return Indexes.of(indexDTO.type(), indexDTO.name(), indexDTO.fieldNames());
  }

  public static Index[] fromDTOs(IndexDTO[] indexDTOS) {
    if (ArrayUtils.isEmpty(indexDTOS)) {
      return Indexes.EMPTY_INDEXES;
    }

    return Arrays.stream(indexDTOS).map(DTOConverters::fromDTO).toArray(Index[]::new);
  }

  public static Partition fromDTO(PartitionDTO partitionDTO) {
    switch (partitionDTO.type()) {
      case IDENTITY:
        IdentityPartitionDTO identityPartitionDTO = (IdentityPartitionDTO) partitionDTO;
        return Partitions.identity(
            identityPartitionDTO.name(),
            identityPartitionDTO.fieldNames(),
            identityPartitionDTO.values(),
            identityPartitionDTO.properties());
      case RANGE:
        RangePartitionDTO rangePartitionDTO = (RangePartitionDTO) partitionDTO;
        return Partitions.range(
            rangePartitionDTO.name(),
            rangePartitionDTO.lower(),
            rangePartitionDTO.upper(),
            rangePartitionDTO.properties());
      case LIST:
        ListPartitionDTO listPartitionDTO = (ListPartitionDTO) partitionDTO;
        return Partitions.list(
            listPartitionDTO.name(), listPartitionDTO.lists(), listPartitionDTO.properties());
      default:
        throw new IllegalArgumentException("Unsupported partition type: " + partitionDTO.type());
    }
  }

  public static SortOrder fromDTO(SortOrderDTO sortOrderDTO) {
    return SortOrders.of(
        fromFunctionArg(sortOrderDTO.sortTerm()),
        sortOrderDTO.direction(),
        sortOrderDTO.nullOrdering());
  }

  public static SortOrder[] fromDTOs(SortOrderDTO[] sortOrderDTO) {
    if (ArrayUtils.isEmpty(sortOrderDTO)) {
      return new SortOrder[0];
    }

    return Arrays.stream(sortOrderDTO).map(DTOConverters::fromDTO).toArray(SortOrder[]::new);
  }

  public static Transform[] fromDTOs(Partitioning[] partitioning) {
    if (ArrayUtils.isEmpty(partitioning)) {
      return new Transform[0];
    }
    return Arrays.stream(partitioning).map(DTOConverters::fromDTO).toArray(Transform[]::new);
  }

  public static Column[] fromDTOs(ColumnDTO[] columns) {
    if (ArrayUtils.isEmpty(columns)) {
      return new Column[0];
    }
    return Arrays.stream(columns).map(DTOConverters::fromDTO).toArray(Column[]::new);
  }

  public static Column fromDTO(ColumnDTO column) {
    if (column.defaultValue().equals(Column.DEFAULT_VALUE_NOT_SET)) {
      return column;
    }
    return Column.of(
        column.name(),
        column.dataType(),
        column.comment(),
        column.nullable(),
        column.autoIncrement(),
        fromFunctionArg((FunctionArg) column.defaultValue()));
  }

  public static Transform fromDTO(Partitioning partitioning) {
    switch (partitioning.strategy()) {
      case IDENTITY:
        return Transforms.identity(
            ((Partitioning.SingleFieldPartitioning) partitioning).fieldName());
      case YEAR:
        return Transforms.year(((Partitioning.SingleFieldPartitioning) partitioning).fieldName());
      case MONTH:
        return Transforms.month(((Partitioning.SingleFieldPartitioning) partitioning).fieldName());
      case DAY:
        return Transforms.day(((Partitioning.SingleFieldPartitioning) partitioning).fieldName());
      case HOUR:
        return Transforms.hour(((Partitioning.SingleFieldPartitioning) partitioning).fieldName());
      case BUCKET:
        return Transforms.bucket(
            ((BucketPartitioningDTO) partitioning).numBuckets(),
            ((BucketPartitioningDTO) partitioning).fieldNames());
      case TRUNCATE:
        return Transforms.truncate(
            ((TruncatePartitioningDTO) partitioning).width(),
            ((TruncatePartitioningDTO) partitioning).fieldName());
      case LIST:
        return Transforms.list(((ListPartitioningDTO) partitioning).fieldNames());
      case RANGE:
        return Transforms.range(((RangePartitioningDTO) partitioning).fieldName());
      case FUNCTION:
        return Transforms.apply(
            ((FunctionPartitioningDTO) partitioning).functionName(),
            fromFunctionArgs(((FunctionPartitioningDTO) partitioning).args()));
      default:
        throw new IllegalArgumentException("Unsupported partitioning: " + partitioning.strategy());
    }
  }
}
