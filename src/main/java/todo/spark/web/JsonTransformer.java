package todo.spark.web;

import spark.ResponseTransformer;

public class JsonTransformer implements ResponseTransformer {

    @Override
    public String render(Object model) {
        return Json.GSON.toJson(model);
    }
}
