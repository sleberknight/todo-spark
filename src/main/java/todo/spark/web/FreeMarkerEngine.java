package todo.spark.web;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import spark.ModelAndView;
import spark.TemplateEngine;

import java.io.IOException;
import java.io.StringWriter;

public class FreeMarkerEngine extends TemplateEngine {

    private final Configuration configuration;

    public FreeMarkerEngine() {
        this.configuration = createConfiguration();
    }

    @Override
    public String render(ModelAndView modelAndView) {
        try {
            StringWriter writer = new StringWriter();
            Template template = configuration.getTemplate(modelAndView.getViewName());
            template.process(modelAndView.getModel(), writer);
            return writer.toString();
        } catch (IOException | TemplateException e) {
            throw new IllegalStateException("Failed to render " + modelAndView.getViewName(), e);
        }
    }

    private Configuration createConfiguration() {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_32);
        configuration.setClassForTemplateLoading(FreeMarkerEngine.class, "/templates");
        return configuration;
    }
}
