package com.bachelorchef.mapper;

import com.bachelorchef.dto.CatalogDtos;
import com.bachelorchef.entity.Ingredient;
import com.bachelorchef.entity.Tag;

public final class CatalogMapper {

    private CatalogMapper() {
    }

    public static CatalogDtos.IngredientView ingredient(Ingredient i) {
        return new CatalogDtos.IngredientView(i.getId(), i.getName(), i.getCategory(), i.getDefaultUnit(), i.isActive());
    }

    public static CatalogDtos.TagView tag(Tag t) {
        return t == null ? null : new CatalogDtos.TagView(t.getId(), t.getName());
    }
}
