package org.doublegsoft.protosys.modelbase;

import com.doublegsoft.apiml.sql.SQLAPIModelExpressionParser;
import com.doublegsoft.jcommons.lang.HashObject;
import com.doublegsoft.jcommons.metabean.AttributeDefinition;
import com.doublegsoft.jcommons.metabean.ModelDefinition;
import com.doublegsoft.jcommons.metabean.ObjectDefinition;
import com.doublegsoft.jcommons.metamodel.ApplicationDefinition;
import com.doublegsoft.jcommons.programming.c.CConventions;
import com.doublegsoft.jcommons.programming.go.GoConventions;
import com.doublegsoft.jcommons.programming.objc.ObjcConventions;
import com.doublegsoft.jcommons.programming.rust.RustConventions;
import com.doublegsoft.jcommons.utils.Inflector;
import com.doublegsoft.jcommons.utils.Strings;
import com.google.gson.Gson;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.DefaultObjectWrapper;
import io.doublegsoft.ablang.AblangContext;
import io.doublegsoft.guidbase.GuidbaseMiniContext;
import io.doublegsoft.tatabase.Tatabase;
import io.doublegsoft.typebase.Typebase;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.doublegsoft.protosys.commons.FileSystemTemplateBasedPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Uses modelbase dsl language approach to develop software.
 *
 * <p>
 *   DDD (Domain Driven Design) example below:
 * <ul>
 *   <li>
 *     {@code
 *     domain =&lt; domain object, default sql, default command, default command handler, default event, default event
 *     handler
 *     }
 *   </li>
 *   <li>
 *     {@code
 *     command (source) => command object, command handler with event trigger (manually), specific sql
 *     }
 *   </li>
 *   <li>
 *     {@code
 *     event => event object, event handler (manually)
 *     }
 *   </li>
 *   <li>
 *     {@code
 *     query (sql) => query object, query handler (manually), common query ?
 *     }
 *   </li>
 *   <li>
 *     {@code
 *     sql => specific sql
 *     }
 *   </li>
 *   <li>
 *     {@code
 *     webapp (command, query) => web controller
 *     }
 *   </li>
 *   <li>
 *     {@code
 *     webapi (command, query) => payload object, api controller
 *     }
 *   </li>
 * </ul>
 *
 * @author <a href="mailto:guo.guo.gan@gmail.com">Christian Gann</a>
 *
 * @since 1.0
 */
public class ModelbasePlugin extends FileSystemTemplateBasedPlugin {

  public static final SQLAPIModelExpressionParser SQL_API_MODEL_EXPRESSION_PARSER = new SQLAPIModelExpressionParser();

  @Override
  public void decorate(ModelDefinition model, HashObject globals) throws IOException {
//    Sqlgen.setIndentCount(2);
    // globals.put("codegen4sql", new SqlManagerCodegen());

    // process value-labelled object
    for (ObjectDefinition obj : model.getObjects()) {
      if (obj.isLabelled("value")) {
        obj.setAlias(obj.getLabelledOptions("value").get("name"));
        if (obj.getAlias() == null) {
          obj.setAlias(obj.getName());
        }
      }
    }
  }

  @Override
  public void decorate(ApplicationDefinition application, HashObject globals) throws IOException {
//    TATABASE.decorate(application, globals);
  }

  private String getPersistenceName(Map<String, String> options) {
    if (options == null || options.isEmpty()) {
      return null;
    }
    return options.get("name");
  }

  /**
   * Generates prototype source code for application definition.
   *
   * @param app
   *        the application definition
   *
   * @param model
   *        the model definition
   *
   * @param outputRoot
   *        the output root directory
   *
   * @param templateRoot
   *        the template root directory
   *
   * @param globals
   *        the global variables
   *
   * @throws IOException
   *        in case of any io errors
   *
   * @since 1.0
   *
   * @version 3.1 - added statics freemarker variable support on Mar 15, 2019 <br>
   */
  public void prototype(ApplicationDefinition app, ModelDefinition model, String outputRoot, String templateRoot, HashObject globals) throws IOException {
    FileTemplateLoader specific = new FileTemplateLoader(new File(templateRoot));
    // FileTemplateLoader specificForTest = new FileTemplateLoader(new File("/Volumes/EXPORT/local/works/doublegsoft.io/modelbase/03.Development/modelbase-data"));
    FileTemplateLoader common = new FileTemplateLoader(new File(templateRoot + "/.."));
    FileTemplateLoader common2 = new FileTemplateLoader(new File(templateRoot + "/../.."));
    MultiTemplateLoader templateLoader = new MultiTemplateLoader(new TemplateLoader[]{common, common2, specific/*, specificForTest*/});
    FREEMARKER.setTemplateLoader(templateLoader);
    FREEMARKER.setSharedVariable("statics", ((DefaultObjectWrapper) FREEMARKER.getObjectWrapper()).getStaticModels());

    decorate(model, globals);
    decorate(app, globals);

    if (globals != null) {
      globalVariables.putAll(globals);
    }
    visitAndRender(outputRoot, "", templateRoot, "", app, new HashObject());
  }

  public static void main(String[] args) throws Exception {
    Options options = new Options();

    options.addOption("m", "model", true, "模型定义文件");
    options.addOption("d", "dependent-model", true, "依赖模型定义文件");
    options.addOption("t", "template-root", true, "模板定义根目录");
    options.addOption("o", "output-root", true, "输出跟路径");
    options.addOption("b", "tatabase", true, "tatabase数据目录");
    options.addOption("l", "license", true, "license数据文件");
    options.addOption("g", "globals", true, "全局常量");
    options.addOption("a", "apifiles", true, "API数据目录");

    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = parser.parse(options, args);

    String modelPath = cmd.getOptionValue("model");
    String dependentModelPath = cmd.getOptionValue("dependent-model");
    String templateRoot = cmd.getOptionValue("template-root");
    String outputRoot = cmd.getOptionValue("output-root");
    String tatabase = cmd.getOptionValue("tatabase");
    String licensePath = cmd.getOptionValue("license");
    String globals = cmd.getOptionValue("globals");
    String apifiles = cmd.getOptionValue("apifiles");

    // globals
    HashObject globalVars = new HashObject();
    Gson gson = new Gson();
    if (globals != null) {
      globalVars.putAll(gson.fromJson(globals, Map.class));
    }

    // license
    String license = null;
    if (licensePath != null) {
      license = new String(Files.readAllBytes(new File(licensePath).toPath()), "UTF-8");
      globalVars.set("license", license);
    }

    // api files for ablang context
    if (apifiles != null) {
      String[] apidbs = apifiles.split(";");
      List<Connection> conns = new ArrayList<>();
      for (String apidb : apidbs) {
        try {
          Class.forName("org.sqlite.JDBC");
          conns.add(DriverManager.getConnection("jdbc:sqlite:" + apidb));
        } catch (SQLException ex) {

        }
      }
      AblangContext context = new AblangContext(conns);
      globalVars.set("apidata", context);

      conns.stream().forEach(conn -> {
        try {
          conn.close();
        } catch (SQLException ex) {
        }
      });
    }

    globalVars.set("typebase", new Typebase());
    globalVars.set("tatabase", new Tatabase());
    globalVars.set("guidbase_mini", new GuidbaseMiniContext());
    // add sql api model parser support
    globalVars.set("sqlapiparser", SQL_API_MODEL_EXPRESSION_PARSER);
    globalVars.set("c", new CConventions());
    globalVars.set("rust", new RustConventions());
    globalVars.set("go", new GoConventions());
    globalVars.set("objc", new ObjcConventions());
    globalVars.set("inflector", new Inflector());

    ModelbasePlugin modelbase = new ModelbasePlugin();

    if (dependentModelPath != null) {
      modelPath += ";" + dependentModelPath;
    }gi
    ModelDefinition model = modelbase.createModelFromModelbase(modelPath.split(";"));
    for (ObjectDefinition obj : model.getObjects()) {
      /*!
      ** Modlebase对Module模块分类的支持。
      **
      ** 2025-01-04
      */
      if (obj.isLabelled("module")) {
        obj.setModuleName(obj.getLabelledOptions("module").get("name"));
      }
    }
    if (dependentModelPath != null) {
      // the dependent models is for reference, not applied to generate source code, and so add generated label
      ModelDefinition dependentModel = modelbase.createModelFromModelbase(dependentModelPath.split(";"));
      for (ObjectDefinition obj : dependentModel.getObjects()) {
        /*!
        ** Modlebase对Module模块分类的支持。
        **
        ** 2025-01-04
        */
        if (obj.isLabelled("module")) {
          obj.setModuleName(obj.getLabelledOptions("module").get("name"));
        }
        ObjectDefinition objInModel = model.findObjectByName(obj.getName());
        objInModel.setLabelledOptions("generated", new HashMap<>());
      }
    }

    /*
    ** 支持继承模式，用object_name_表示该对象定义继承了object_name的对象定义。
    ** 同时需要把新定义的合并到原有的模型定义上。
    **
    ** @version 6.0
    */
    for (ObjectDefinition obj : model.getObjects()) {
      if (obj.getName().endsWith("_")) {
        String origname = obj.getName().substring(0, obj.getName().length() - 1);
        /*!
        ** 已经定义的对象。
        */
        ObjectDefinition origobj = model.findObjectByName(origname);
        obj.getLabelledOptions().putAll(origobj.getLabelledOptions());
        obj.getLabelledOptions().remove("generated");
        obj.setName(origname);
        obj.setPersistenceName(origobj.getPersistenceName());
        obj.setModuleName(origobj.getModuleName());
        obj.setAlias(origobj.getAlias());
        obj.setPlural(origobj.getPlural());
        /*!
        ** 重新标注新定义的属性，区分已有的属性。
        */
        for (AttributeDefinition attr : obj.getAttributes()) {
          attr.setLabelledOptions("redefined", new HashMap<>());
        }
        for (AttributeDefinition origattr : origobj.getAttributes()) {
          boolean existing = false;
          for (AttributeDefinition attr : obj.getAttributes()) {
            if (attr.getName().equals(origattr.getName())) {
              Map<String, Map<String, String>> opts = new HashMap<>();
              for (Map.Entry<String, Map<String, String>> entry : origattr.getLabelledOptions().entrySet()) {
                if (opts.containsKey(entry.getKey())) {
                  Map<String,String> existings = opts.get(entry.getKey());
                  existings.putAll(entry.getValue());
                  opts.put(entry.getKey(), existings);
                } else {
                  opts.put(entry.getKey(), entry.getValue());
                }
              }
              for (Map.Entry<String, Map<String, String>> entry : attr.getLabelledOptions().entrySet()) {
                if (opts.containsKey(entry.getKey())) {
                  Map<String,String> existings = opts.get(entry.getKey());
                  existings.putAll(entry.getValue());
                  opts.put(entry.getKey(), existings);
                } else {
                  opts.put(entry.getKey(), entry.getValue());
                }
              }
              for (Map.Entry<String, Map<String, String>> entry : opts.entrySet()) {
                attr.setLabelledOptions(entry.getKey(), entry.getValue());
              }
              attr.setPersistenceName(origattr.getPersistenceName());
              existing = true;
              break;
            }
          }
          if (!existing) {
            obj.addAttribute(origattr);
          }
        }
      } else if (obj.isLabelled("pivot")) {
        String master = obj.getLabelledOptions("pivot").get("master");
        String detail = obj.getLabelledOptions("pivot").get("detail");
        String key = obj.getLabelledOptions("pivot").get("detail");
        String value = obj.getLabelledOptions("pivot").get("value");
        ObjectDefinition detailObj = model.findObjectByName(detail);

        if (master != null) {
          ObjectDefinition masterObj = model.findObjectByName(master);
          for (AttributeDefinition attr : masterObj.getAttributes()) {
            obj.addAttribute(attr);
          }
        }
        for (AttributeDefinition attr : obj.getAttributes()) {
          AttributeDefinition realAttr = null;
          if (master != null) {
            realAttr = model.findAttributeByNames(master, attr.getName());
          }
          if (realAttr == null) {
            attr.setLabelledOptions("redefined", new HashMap<>());
          }
        }
      }
    }

    ApplicationDefinition app = new ApplicationDefinition();

    String applicationName = globalVars.get("application");
    String databaseName = globalVars.get("database");
    if (applicationName == null) {
      applicationName = globalVars.get("application");
    }
    app.setName(applicationName);
    app.setModel(model);

    String namingClass = globalVars.get("naming");
    if (namingClass != null) {
      Object naming = Class.forName(namingClass).newInstance();
      globalVars.set("naming", naming);
    }

    namingClass = globalVars.get("globalNamingConvention");
    if (namingClass != null) {
      Object naming = Class.forName(namingClass).newInstance();
      globalVars.set("globalNamingConvention", naming);
    }

    for (ObjectDefinition obj : model.getObjects()) {
      if (obj.isLabelled("generated")) {
        // ignore dependant modules
        continue;
      }
      if (!Strings.isEmpty(databaseName)) {
        if (obj.isLabelled("persistence")) {
          obj.getLabelledOptions("persistence").put("namespace", databaseName);
        }
      }
      Map<String, String> moduleOpts = new HashMap<>();
      moduleOpts.putAll(obj.getLabelledOptions("module"));
      if (obj.getModuleName() == null) {
        obj.setModuleName(applicationName);
        moduleOpts.put("name", applicationName);
      }
      obj.setLabelledOptions("module", moduleOpts);
    }

    try {
      modelbase.prototype(app, model, outputRoot, templateRoot, globalVars);
    } catch (Throwable cause) {
      cause.printStackTrace();
      System.out.println(cause.getMessage());
    }

  }

}
