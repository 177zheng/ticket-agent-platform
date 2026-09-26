package com.ticketplatform.web;

import com.ticketplatform.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 前端展示用的元信息。 */
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    private final AppProperties props;

    public MetaController(AppProperties props) {
        this.props = props;
    }

    @GetMapping
    public Map<String, Object> meta() {
        return Map.of(
                "llmMode", props.getLlm().getMode(),
                "model", props.getLlm().getModel());
    }
}
