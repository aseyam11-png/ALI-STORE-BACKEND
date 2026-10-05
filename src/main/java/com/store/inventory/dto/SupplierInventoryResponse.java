package com.store.inventory.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SupplierInventoryResponse {
    private List<SupplierInventoryItem> items = new ArrayList<>();
}
