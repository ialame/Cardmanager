package com.pcagrade.retriever.localization.web;

import com.pcagrade.mason.localization.Localization;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/localizations")
public class LocalizationRestController {

    @GetMapping("/groups/{code}")
    public List<String> getLocalizationGroup(@PathVariable String code) {
        Localization.Group group = Localization.Group.getByCode(code);
        return group.getLocalizations().stream()
                .map(Localization::getCode)
                .toList();
    }
}