package com.xiaoying.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xiaoying.common.ApiException;
import com.xiaoying.common.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateServiceTest {

    private final FakeTemplateMapper mapper = new FakeTemplateMapper();
    private final TemplateService service = new TemplateService(mapper);

    @Test
    void mapsAcceptedMimeTypesAndSkipsBlankCategoryFilter() {
        mapper.enabled.add(row("a", "创意图片"));
        mapper.enabled.add(row("b", "宠物写真"));

        List<TemplateDto> result = service.list(" ");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).acceptedMimeTypes()).containsExactly("image/jpeg", "image/png");
        assertThat(result.get(0).maxFileSizeBytes()).isEqualTo(5_242_880L);
    }

    @Test
    void filtersByCategory() {
        mapper.enabled.add(row("a", "创意图片"));
        mapper.enabled.add(row("b", "宠物写真"));

        assertThat(service.list("宠物写真"))
                .extracting(TemplateDto::id)
                .containsExactly("b");
    }

    @Test
    void missingOrDisabledTemplateIs404() {
        assertThatThrownBy(() -> service.get("gone"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.TEMPLATE_NOT_FOUND);
    }

    @Test
    void categoriesCarryCounts() {
        mapper.counts.add(count("创意图片", 2));

        assertThat(service.categories()).containsExactly(new CategoryDto("创意图片", 2));
    }

    private static TemplateRow row(String id, String category) {
        TemplateRow row = new TemplateRow();
        row.setId(id);
        row.setTitle(id);
        row.setCategory(category);
        row.setCoverUrl("/demo/" + id + ".png");
        row.setRequiredImageCount(2);
        row.setAcceptedMimeTypes("image/jpeg, image/png");
        row.setMaxFileSizeBytes(5_242_880L);
        row.setEnabled(true);
        row.setSortOrder(10);
        return row;
    }

    private static CategoryCountRow count(String category, long value) {
        CategoryCountRow row = new CategoryCountRow();
        row.setCategory(category);
        row.setCnt(value);
        return row;
    }

    static final class FakeTemplateMapper implements TemplateMapper {

        final List<TemplateRow> enabled = new ArrayList<>();
        final List<CategoryCountRow> counts = new ArrayList<>();

        @Override
        public List<TemplateRow> findEnabled() {
            return enabled;
        }

        @Override
        public List<TemplateRow> findEnabledByCategory(String category) {
            return enabled.stream().filter(row -> row.getCategory().equals(category)).toList();
        }

        @Override
        public TemplateRow findEnabledById(String id) {
            return enabled.stream().filter(row -> row.getId().equals(id)).findFirst().orElse(null);
        }

        @Override
        public List<CategoryCountRow> countByCategory() {
            return counts;
        }
    }
}