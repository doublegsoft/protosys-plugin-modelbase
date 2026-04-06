package org.doublegsoft.protosys.modelbase;

import com.doublegsoft.jcommons.metabean.ModelDefinition;
import io.doublegsoft.modelbase.Modelbase;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

public class ModelbasePluginTest {

  @Test
  public void test_enum() throws Exception {
    String expr = new String(Files.readAllBytes(
        new File("src/test/resources/V7/enum.modelbase").toPath()), "UTF-8");
    ModelDefinition model = new Modelbase().parse(expr);
  }

}
