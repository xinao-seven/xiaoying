package com.xiaoying.support;

import com.xiaoying.material.MaterialMapper;
import com.xiaoying.material.MaterialRow;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMaterialMapper implements MaterialMapper {

    public final Map<String, MaterialRow> rows = new ConcurrentHashMap<>();

    @Override
    public void insert(MaterialRow row) {
        rows.put(row.getId(), copy(row));
    }

    @Override
    public MaterialRow findById(String id) {
        MaterialRow row = rows.get(id);
        return row == null ? null : copy(row);
    }

    public static MaterialRow material(String id, String userId, String mimeType, long sizeBytes,
                                       String status) {
        MaterialRow row = new MaterialRow();
        row.setId(id);
        row.setUserId(userId);
        row.setOriginalName(id + ".png");
        row.setStorageKey("uploads/" + userId + "/" + id + ".png");
        row.setMimeType(mimeType);
        row.setSizeBytes(sizeBytes);
        row.setWidth(100);
        row.setHeight(100);
        row.setStatus(status);
        return row;
    }

    private static MaterialRow copy(MaterialRow source) {
        MaterialRow target = new MaterialRow();
        target.setId(source.getId());
        target.setUserId(source.getUserId());
        target.setOriginalName(source.getOriginalName());
        target.setStorageKey(source.getStorageKey());
        target.setMimeType(source.getMimeType());
        target.setSizeBytes(source.getSizeBytes());
        target.setWidth(source.getWidth());
        target.setHeight(source.getHeight());
        target.setStatus(source.getStatus());
        target.setCreatedAt(source.getCreatedAt());
        return target;
    }
}