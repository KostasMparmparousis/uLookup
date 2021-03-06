package gr.gunet.uLookup;
import gr.gunet.uLookup.routes.security.ValidateToken;
import gr.gunet.uLookup.filters.BasicAuthFilter;
import gr.gunet.uLookup.routes.*;
import gr.gunet.uLookup.routes.security.LogoutHandle;
import spark.Request;
import spark.Response;
import spark.Spark;
import spark.servlet.SparkApplication;
import spark.staticfiles.StaticFilesConfiguration;


public class Main implements SparkApplication {
    public Main(){}
    @Override
    public void init(){
        Spark.ipAddress("127.0.0.1");
        BasicAuthFilter authFilter = new BasicAuthFilter();
        Spark.before("/loginNameValidator/", authFilter);
        Spark.before("/loginNameValidator", authFilter);
        Spark.before("/loginNameProposer/", authFilter);
        Spark.before("/loginNameProposer", authFilter);
        Spark.before("/roleFinder/", authFilter);
        Spark.before("/roleFinder", authFilter);
        Spark.before("/validator.html", authFilter);
        Spark.before("/proposer.html", authFilter);
        Spark.before("/roleFinder.html", authFilter);

        ValidateToken validateToken;
        try{
            validateToken = new ValidateToken();
        }catch(Exception e){
            e.printStackTrace(System.err);
            Spark.stop();
            return;
        }

        LogoutHandle logoutHandle;
        try{
            logoutHandle = new LogoutHandle();
        }catch(Exception e){
            e.printStackTrace(System.err);
            Spark.stop();
            return;
        }

        Spark.post("/validate-token",validateToken);
        Spark.post("/validate-token/",validateToken);
        Spark.post("/logout",logoutHandle);
        Spark.post("/logout/",logoutHandle);

        Spark.post("/loginNameValidator/", new LoginNameValidatorRoute());
        Spark.post("/loginNameValidator", new LoginNameValidatorRoute());
        Spark.post("/loginNameProposer/", new LoginNameProposerRoute());
        Spark.post("/loginNameProposer", new LoginNameProposerRoute());
        Spark.post("/roleFinder/", new RoleFinderRoute());
        Spark.post("/roleFinder", new RoleFinderRoute());
        Spark.get("/help/", new HelpPageRoute());
        Spark.get("/help", new HelpPageRoute());

        StaticFilesConfiguration staticHandler = new StaticFilesConfiguration();
        staticHandler.configure("/static");
        Spark.before((request, response) -> staticHandler.consume(request.raw(), response.raw()));

        Spark.get("/index/", (req,res) -> returnStatic(req,res));
        CleanupThread cleanThread= new CleanupThread();
        cleanThread.run();

    }

    @Override
    public void destroy(){}

    public static void main(String[] args){
        new Main().init();
    }

    public static String returnStatic(Request request,Response response){
        response.redirect(ServerConfigurations.getConfiguration("base_url")+"index.html");
        return null;
    }
}