/*
 * Copyright 2023 Datastrato Pvt Ltd.
 * This software is licensed under the Apache License version 2.
 */
package com.datastrato.gravitino.dto.rel;

import com.google.common.base.Preconditions;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ArrayUtils;

public class PartitionUtils {

  public static void validateFieldExistence(ColumnDTO[] columns, String[] fieldName)
      throws IllegalArgumentException {
    Preconditions.checkArgument(ArrayUtils.isNotEmpty(columns), "columns cannot be null or empty");

    List<ColumnDTO> partitionColumn =
        Arrays.stream(columns)
            // (TODO) Need to consider the case sensitivity issues.
            //   To be optimized.
            .filter(c -> c.name().equalsIgnoreCase(fieldName[0]))
            .collect(Collectors.toList());
    Preconditions.checkArgument(
        partitionColumn.size() == 1, "partition field %s not found in table", fieldName[0]);

    // TODO: should validate nested fieldName after column type support namedStruct
  }
}
