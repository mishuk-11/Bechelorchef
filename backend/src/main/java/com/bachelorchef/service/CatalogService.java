package com.bachelorchef.service;

import com.bachelorchef.dto.CatalogDtos;
import com.bachelorchef.dto.PageResponse;
import com.bachelorchef.dto.Pagination;
import com.bachelorchef.entity.Ingredient;
import com.bachelorchef.entity.Tag;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.mapper.CatalogMapper;
import com.bachelorchef.repository.IngredientRepository;
import com.bachelorchef.repository.TagRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingredient and tag catalogue. Uniqueness is by normalised (trimmed, case-folded) name, enforced
 * here for friendly messages and by unique constraints in the database. Renaming keeps the same
 * id, so recipe links and pantry matching (which use ids) are unaffected.
 */
@Service
public class CatalogService {

    private static final Map<String, Sort> INGREDIENT_SORTS = Map.of(
            "name", Sort.by("name").ascending().and(Sort.by("id")),
            "category", Sort.by("category").ascending().and(Sort.by("name")),
            "newest", Sort.by("createdAt").descending().and(Sort.by("id")));

    private final IngredientRepository ingredients;
    private final TagRepository tags;
    private final AuditService audit;

    public CatalogService(IngredientRepository ingredients, TagRepository tags, AuditService audit) {
        this.ingredients = ingredients;
        this.tags = tags;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ ingredients

    @Transactional(readOnly = true)
    public PageResponse<CatalogDtos.IngredientView> searchIngredients(String query, String category, boolean includeInactive,
                                                                      String sort, Integer page, Integer size) {
        String key = TextNormalizer.key(query);
        String cat = TextNormalizer.clean(category);
        Specification<Ingredient> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!includeInactive) {
                predicates.add(cb.isTrue(root.get("active")));
            }
            if (key != null) {
                predicates.add(cb.like(root.get("normalizedName"), "%" + escapeLike(key) + "%", '\\'));
            }
            if (cat != null) {
                predicates.add(cb.equal(cb.lower(root.get("category")), cat.toLowerCase(java.util.Locale.ROOT)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.of(ingredients.findAll(spec, Pagination.of(page, size, Pagination.sort(sort, INGREDIENT_SORTS, "name"))),
                CatalogMapper::ingredient);
    }

    @Transactional(readOnly = true)
    public List<String> ingredientCategories() {
        return ingredients.findCategories();
    }

    @Transactional
    public CatalogDtos.IngredientView createIngredient(CatalogDtos.IngredientRequest request, Long actorId) {
        String name = requireName(request.name(), 80);
        String key = TextNormalizer.key(name);
        if (ingredients.findByNormalizedName(key).isPresent()) {
            throw ApiException.conflict("An ingredient named \"" + name + "\" already exists.");
        }
        Ingredient ingredient = new Ingredient();
        ingredient.setName(name);
        ingredient.setNormalizedName(key);
        ingredient.setCategory(TextNormalizer.clean(request.category()));
        ingredient.setDefaultUnit(TextNormalizer.clean(request.defaultUnit()));
        ingredients.save(ingredient);
        audit.record(actorId, "INGREDIENT_CREATED", "INGREDIENT", ingredient.getId(), "name=" + name);
        return CatalogMapper.ingredient(ingredient);
    }

    @Transactional
    public CatalogDtos.IngredientView updateIngredient(Long id, CatalogDtos.IngredientRequest request, Long actorId) {
        Ingredient ingredient = ingredients.findById(id).orElseThrow(() -> ApiException.notFound("Ingredient"));
        String name = requireName(request.name(), 80);
        String key = TextNormalizer.key(name);
        ingredients.findByNormalizedName(key)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw ApiException.conflict("An ingredient named \"" + other.getName() + "\" already exists.");
                });
        String oldName = ingredient.getName();
        ingredient.setName(name);
        ingredient.setNormalizedName(key);
        ingredient.setCategory(TextNormalizer.clean(request.category()));
        ingredient.setDefaultUnit(TextNormalizer.clean(request.defaultUnit()));
        audit.record(actorId, "INGREDIENT_UPDATED", "INGREDIENT", id, "from=" + oldName + "; to=" + name);
        return CatalogMapper.ingredient(ingredient);
    }

    @Transactional
    public CatalogDtos.IngredientView setIngredientActive(Long id, boolean active, Long actorId) {
        Ingredient ingredient = ingredients.findById(id).orElseThrow(() -> ApiException.notFound("Ingredient"));
        ingredient.setActive(active);
        audit.record(actorId, active ? "INGREDIENT_ACTIVATED" : "INGREDIENT_DEACTIVATED", "INGREDIENT", id, ingredient.getName());
        return CatalogMapper.ingredient(ingredient);
    }

    /** Loads ingredients by id, keyed by id. Missing ids are simply absent from the map. */
    @Transactional(readOnly = true)
    public Map<Long, Ingredient> ingredientsById(Collection<Long> ids) {
        return ingredients.findAllById(ids).stream().collect(Collectors.toMap(Ingredient::getId, Function.identity()));
    }

    // ------------------------------------------------------------------ tags

    @Transactional(readOnly = true)
    public List<CatalogDtos.TagView> listTags() {
        return tags.findAllByOrderByNameAsc().stream().map(CatalogMapper::tag).toList();
    }

    @Transactional
    public CatalogDtos.TagView createTag(CatalogDtos.TagRequest request, Long actorId) {
        String name = requireName(request.name(), 40);
        if (tags.findByNormalizedName(TextNormalizer.key(name)).isPresent()) {
            throw ApiException.conflict("A tag named \"" + name + "\" already exists.");
        }
        Tag tag = newTag(name);
        audit.record(actorId, "TAG_CREATED", "TAG", tag.getId(), "name=" + name);
        return CatalogMapper.tag(tag);
    }

    @Transactional
    public CatalogDtos.TagView renameTag(Long id, CatalogDtos.TagRequest request, Long actorId) {
        Tag tag = tags.findById(id).orElseThrow(() -> ApiException.notFound("Tag"));
        String name = requireName(request.name(), 40);
        String key = TextNormalizer.key(name);
        tags.findByNormalizedName(key).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw ApiException.conflict("A tag named \"" + other.getName() + "\" already exists.");
        });
        tag.setName(name);
        tag.setNormalizedName(key);
        audit.record(actorId, "TAG_RENAMED", "TAG", id, "name=" + name);
        return CatalogMapper.tag(tag);
    }

    /**
     * Resolves tag names case-insensitively. When {@code allowCreate} is false (community users),
     * unknown tags produce a validation error; admins and imports may create new tags.
     */
    @Transactional
    public Set<Tag> resolveTags(List<String> names, boolean allowCreate) {
        if (names == null || names.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Map<String, String> byKey = new LinkedHashMap<>();
        for (String raw : names) {
            String cleaned = TextNormalizer.clean(raw);
            if (cleaned != null) {
                byKey.putIfAbsent(cleaned.toLowerCase(java.util.Locale.ROOT), cleaned);
            }
        }
        Map<String, Tag> existing = tags.findByNormalizedNameIn(byKey.keySet()).stream()
                .collect(Collectors.toMap(Tag::getNormalizedName, Function.identity()));
        Set<Tag> result = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : byKey.entrySet()) {
            Tag tag = existing.get(entry.getKey());
            if (tag == null) {
                if (!allowCreate) {
                    throw ApiException.validation("tags", "Unknown tag \"" + entry.getValue() + "\". Choose from the tag list.");
                }
                tag = newTag(entry.getValue());
            }
            result.add(tag);
        }
        return result;
    }

    /** Import policy: an unknown ingredient name creates a new active catalogue entry. */
    @Transactional
    public Ingredient findOrCreateIngredient(String name) {
        String cleaned = TextNormalizer.clean(name);
        String key = TextNormalizer.key(cleaned);
        return ingredients.findByNormalizedName(key).orElseGet(() -> {
            Ingredient ingredient = new Ingredient();
            ingredient.setName(cleaned);
            ingredient.setNormalizedName(key);
            ingredient.setCategory("Uncategorized");
            return ingredients.save(ingredient);
        });
    }

    private Tag newTag(String name) {
        Tag tag = new Tag();
        tag.setName(name);
        tag.setNormalizedName(TextNormalizer.key(name));
        return tags.save(tag);
    }

    private static String requireName(String value, int max) {
        String name = TextNormalizer.clean(value);
        if (name == null || TextNormalizer.length(name) > max) {
            throw ApiException.validation("name", "Name is required (max " + max + " characters).");
        }
        return name;
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
