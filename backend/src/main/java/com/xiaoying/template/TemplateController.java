package com.xiaoying.template;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping("/categories")
    public CategoryListResponse categories() {
        return new CategoryListResponse(templateService.categories());
    }

    @GetMapping
    public TemplateListResponse list(@RequestParam(name = "category", required = false) String category) {
        return new TemplateListResponse(templateService.list(category));
    }

    @GetMapping("/{id}")
    public TemplateDto detail(@PathVariable("id") String id) {
        return templateService.get(id);
    }

    public record TemplateListResponse(List<TemplateDto> items) {
    }

    public record CategoryListResponse(List<CategoryDto> items) {
    }
}