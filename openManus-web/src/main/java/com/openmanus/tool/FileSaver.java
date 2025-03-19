package com.openmanus.tool;


import com.openmanus.tool.base.ToolResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Component
@Slf4j
public class FileSaver implements Tool {

  private final Vertx vertx;

  public FileSaver() {
    this.vertx = Vertx.vertx();
  }

  @Override
  public String getName() {
    return "file_saver";
  }

  @Override
  public String getDescription() {
    return """
                Save content to a local file at a specified path.
                Use this tool when you need to save text, code, or generated content to a file on the local filesystem.
                The tool accepts content and a file path, and saves the content to that location.
                """;
  }

  @Override
  public JsonObject getParameters() {
    JsonObject parameters = new JsonObject();
    parameters.put("type", "object");
    JsonObject properties = new JsonObject();
    properties.put("content", new JsonObject()
      .put("type", "string")
      .put("description", "(required) The content to save to the file."));
    properties.put("file_path", new JsonObject()
      .put("type", "string")
      .put("description", "(required) The path where the file should be saved, including filename and extension."));
    properties.put("mode", new JsonObject()
      .put("type", "string")
      .put("description", "(optional) The file opening mode. Default is 'w' for write. Use 'a' for append.")
      .put("enum", List.of("w", "a"))
      .put("default", "w"));
    parameters.put("properties", properties);
    List<String> required = List.of("content", "file_path");
    parameters.put("required", required);
    return parameters;
  }

  @Override
  public Future<Object> execute(JsonObject toolInput) {
    Promise<Object> promise = Promise.promise();
    String content = toolInput.getString("content");
    String filePath = toolInput.getString("file_path");
    String mode = toolInput.getString("mode", "w");

    if (content == null || filePath == null) {
      promise.complete(new ToolResult("Content and file_path are required").toJson());
      return promise.future();
    }

    FileSystem fileSystem = vertx.fileSystem();
    File file = new File(filePath);
    String directory = file.getParent();

    Future<Void> ensureDirectoryExists;
    if (directory != null) {
      ensureDirectoryExists = fileSystem.mkdirs(directory);
    } else {
      ensureDirectoryExists = Future.succeededFuture();
    }

    ensureDirectoryExists.compose(v -> {
      OpenOptions openOptions = new OpenOptions();
      if ("w".equals(mode)) {
        openOptions.setWrite(true).setCreate(true).setTruncateExisting(true);
      } else if ("a".equals(mode)) {
        openOptions.setWrite(true).setCreate(true).setAppend(true);
      } else {
        promise.complete(new ToolResult("Invalid mode: " + mode).toJson());
        return Future.failedFuture("Invalid mode: " + mode);
      }
      return fileSystem.open(filePath, openOptions);
    }).compose(asyncFile -> {
      return asyncFile.write(io.vertx.core.buffer.Buffer.buffer(content));
    }).onSuccess(v -> {
      promise.complete(new ToolResult("Content successfully saved to " + filePath).toJson());
    }).onFailure(throwable -> {
      promise.complete(new ToolResult("Error saving file: " + throwable.getMessage()).toJson());
    });

    return promise.future();
  }
}
