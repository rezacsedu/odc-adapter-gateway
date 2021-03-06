package de.fraunhofer.fokus.ids.main;

import de.fraunhofer.fokus.ids.services.webclient.WebClientService;
import de.fraunhofer.fokus.ids.services.webclient.WebClientServiceVerticle;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.*;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import org.apache.http.entity.ContentType;

import java.util.*;
/**
 * @author Vincent Bohlen, vincent.bohlen@fokus.fraunhofer.de
 */
public class MainVerticle extends AbstractVerticle {
    private static Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class.getName());
    private Router router;
    private WebClientService webClientService;
    private int configManagerPort;
    private String configManagerHost;
    private int servicePort;

    @Override
    public void start(Future<Void> startFuture) {
        this.router = Router.router(vertx);

        DeploymentOptions deploymentOptions = new DeploymentOptions();
        deploymentOptions.setWorker(true);


        vertx.deployVerticle(WebClientServiceVerticle.class.getName(), deploymentOptions, reply -> {
            if(reply.succeeded()){
                LOGGER.info("WebClientService started");
                this.webClientService = WebClientService.createProxy(vertx, "de.fraunhofer.fokus.ids.webServiceClient");
            }
        });

        ConfigStoreOptions confStore = new ConfigStoreOptions()
                .setType("env");

        ConfigRetrieverOptions options = new ConfigRetrieverOptions().addStore(confStore);

        ConfigRetriever retriever = ConfigRetriever.create(vertx, options);

        retriever.getConfig(ar -> {
            if (ar.succeeded()) {
                this.configManagerPort = ar.result().getInteger("CONFIG_MANAGER_PORT");
                this.configManagerHost = ar.result().getString("CONFIG_MANAGER_URL");
                this.servicePort = ar.result().getInteger("SERVICE_PORT");
                createHttpServer();
            } else {
                LOGGER.error("Config could not be retrieved.", ar.cause());
            }
        });

    }

    private void createHttpServer() {
        HttpServer server = vertx.createHttpServer();

        Set<String> allowedHeaders = new HashSet<>();
        allowedHeaders.add("x-requested-with");
        allowedHeaders.add("Access-Control-Allow-Origin");
        allowedHeaders.add("origin");
        allowedHeaders.add("Content-Type");
        allowedHeaders.add("accept");
        allowedHeaders.add("X-PINGARUNER");

        Set<HttpMethod> allowedMethods = new HashSet<>();
        allowedMethods.add(HttpMethod.GET);
        allowedMethods.add(HttpMethod.POST);

        router.route().handler(CorsHandler.create("*").allowedHeaders(allowedHeaders).allowedMethods(allowedMethods));
        router.route().handler(BodyHandler.create());

        router.post("/create/:name").handler(routingContext ->
                create(routingContext.request().getParam("name"), routingContext.getBodyAsJson(), reply -> reply(reply, routingContext.response())));

        router.route("/delete/:name/:id").handler(routingContext ->
                delete(routingContext.request().getParam("name"), Long.parseLong(routingContext.request().getParam("id")), reply -> reply(reply, routingContext.response())));

        router.post("/getFile/:name").handler(routingContext ->
                getFile(routingContext.request().getParam("name"), routingContext.getBodyAsJson(), reply -> reply(reply, routingContext.response())));

        router.route("/supported/:name").handler(routingContext ->
                supported(routingContext.request().getParam("name"), reply -> reply(reply, routingContext.response())));

        router.route("/getDataSourceFormSchema/:type").handler(routingContext ->
                getDataSourceFormSchema(routingContext.request().getParam("type"), reply -> reply(reply, routingContext.response())));
        router.route("/getDataAssetFormSchema/:type").handler(routingContext ->
                getDataAssetFormSchema(routingContext.request().getParam("type"), reply -> reply(reply, routingContext.response())));
        LOGGER.info("Starting Adapter Gateway...");
        server.requestHandler(router).listen(servicePort);
        LOGGER.info("Adapter Gateway successfully started on port "+servicePort);
    }

    private void getDataAssetFormSchema(String type, Handler<AsyncResult<JsonObject>> resultHandler ){
        webClientService.get(configManagerPort, configManagerHost,"/getAdapter/"+type, reply -> {
            if(reply.succeeded()){
                webClientService.get(reply.result().getInteger("port"), reply.result().getString("host"), "/getDataAssetFormSchema/", reply2 -> {
                    if(reply2.succeeded()){
                        resultHandler.handle(Future.succeededFuture(reply2.result()));
                    }
                    else{
                        LOGGER.error("Create failed in adapter",reply2.cause());
                        resultHandler.handle(Future.failedFuture(reply2.cause()));
                    }
                });
            }
            else{
                LOGGER.error("Adapter could not be retrieved.",reply.cause());
            }
        });
    }

    private void getDataSourceFormSchema(String type, Handler<AsyncResult<JsonObject>> resultHandler){
        webClientService.get(configManagerPort, configManagerHost,"/getAdapter/"+type, reply -> {
            if(reply.succeeded()){
                webClientService.get(reply.result().getInteger("port"), reply.result().getString("host"), "/getDataSourceFormSchema/", reply2 -> {
                    if(reply2.succeeded()){
                        resultHandler.handle(Future.succeededFuture(reply2.result()));
                    }
                    else{
                        LOGGER.error("Create failed in adapter",reply2.cause());
                        resultHandler.handle(Future.failedFuture(reply2.cause()));
                    }
                });
            }
            else{
                LOGGER.error("Adapter could not be retrieved.",reply.cause());
            }
        });
    }

    private void supported(String name, Handler<AsyncResult<JsonObject>> resultHandler){
        webClientService.get(configManagerPort, configManagerHost,"/getAdapter/"+name, reply -> {
            if(reply.succeeded()){
                webClientService.get(reply.result().getInteger("port"), reply.result().getString("host"), "/supported/", reply2 -> {
                    if(reply2.succeeded()){
                        resultHandler.handle(Future.succeededFuture(reply2.result()));
                    }
                    else{
                        LOGGER.error("Create failed in adapter",reply2.cause());
                        resultHandler.handle(Future.failedFuture(reply2.cause()));
                    }
                });
            }
            else{
                LOGGER.error("Adapter could not be retrieved.",reply.cause());
            }
        });
    }

    private void getFile(String name, JsonObject jsonObject, Handler<AsyncResult<JsonObject>> resultHandler){
        webClientService.get(configManagerPort, configManagerHost,"/getAdapter/"+name, reply -> {
            if(reply.succeeded()){
                webClientService.post(reply.result().getInteger("port"), reply.result().getString("host"), "/getFile", jsonObject, reply2 -> {
                    if(reply2.succeeded()){
                        resultHandler.handle(Future.succeededFuture(reply2.result()));
                    }
                    else{
                        LOGGER.error("Create failed in adapter",reply2.cause());
                        resultHandler.handle(Future.failedFuture(reply2.cause()));
                    }
                });
            }
            else{
                LOGGER.error("Adapter could not be retrieved.",reply.cause());
            }
        });
    }

    private void delete(String name, Long id, Handler<AsyncResult<JsonObject>> resultHandler){
        webClientService.get(configManagerPort, configManagerHost,"/getAdapter/"+name, reply -> {
            if(reply.succeeded()){
                webClientService.get(reply.result().getInteger("port"), reply.result().getString("host"), "/delete/"+id, reply2 -> {
                    if(reply2.succeeded()){
                        resultHandler.handle(Future.succeededFuture(reply2.result()));
                    }
                    else{
                        LOGGER.error("Delete failed in adapter",reply2.cause());
                        resultHandler.handle(Future.failedFuture(reply2.cause()));
                    }
                });
            }
            else{
                LOGGER.error("Adapter could not be retrieved.",reply.cause());
            }
        });
    }

    private void create(String name, JsonObject request, Handler<AsyncResult<JsonObject>> resultHandler){
        webClientService.get(configManagerPort, configManagerHost,"/getAdapter/"+name, reply -> {
            if(reply.succeeded()){
                webClientService.post(reply.result().getInteger("port"), reply.result().getString("host"), "/create", request, reply2 -> {
                    if(reply2.succeeded()){
                        resultHandler.handle(Future.succeededFuture(reply2.result()));
                    }
                    else{
                        LOGGER.error("Create failed in adapter",reply2.cause());
                        resultHandler.handle(Future.failedFuture(reply2.cause()));
                    }
                });
            }
            else{
                LOGGER.error("Adapter could not be retrieved.",reply.cause());
            }
        });
    }

    private void reply(AsyncResult result, HttpServerResponse response) {
        if (result.succeeded()) {
            if (result.result() != null) {
                String entity = result.result().toString();
                response.putHeader("content-type", ContentType.APPLICATION_JSON.toString());
                response.end(entity);
            } else {
                response.setStatusCode(404).end();
            }
        } else {
            response.setStatusCode(404).end();
        }
    }

    public static void main(String[] args) {
        String[] params = Arrays.copyOf(args, args.length + 1);
        params[params.length - 1] = MainVerticle.class.getName();
        Launcher.executeCommand("run", params);
    }
}