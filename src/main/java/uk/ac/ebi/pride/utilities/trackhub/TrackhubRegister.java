package uk.ac.ebi.pride.utilities.trackhub;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * This class will login to the supplied Registry, post a track hub, and logout.
 *
 * @author Tobias Ternent
 */
public class TrackhubRegister {

    public static final Logger logger = LoggerFactory.getLogger(TrackhubRegister.class);

    private String server;
    private String user;
    private String password;
    private String authToken;
    private String url;
    private PostType postType;
    private SearchType searchType;
    private List<Assembly> assemblies;

    /**
     * Genome assembly restricted list.
     */
    public enum Assembly {
        hg38("GCA_000001405.21");

        private String INSDC;

        Assembly(String insdc) {
            this.INSDC = insdc;
        }
        public String getINSDC() {
            return INSDC;
        }
    } //TODO Perhaps this should be more flexible and allow this to be set by a String? OTherwise it will need to be updated constantly as required.

    /**
     * Restricted list of allowable track hub post types.
     */
    public enum PostType {GENOMICS, EPIGENOMICS, TRANSCRIPTOMICS, PROTEOMICS}

    /**
     * Restricted list of allowable search types.
     */
    public enum SearchType {PUBLIC(1), PRIVATE(0);
        private final int value;
        SearchType(int value) {
            this.value = value;
        }
        public int getValue() {
            return this.value;
        }
    }


    /**
     * Default constructor. All parameters will need to be specified separately.
     */
    public TrackhubRegister() {
    }

    /**
     * Constructor with all the necessary parameters.
     * @param server The address of the Registry server.
     * @param user The input username for the Registry account.
     * @param password The input password for the Registry account.
     * @param url The URL of the trackhub, HTTP preferred over FTP.
     * @param postType The -omics type of the track hub.
     * @param searchType Should the track hub be visible in Registry search results or not.
     * @param assemblies The assemblies present on the track hub.
     */
    public TrackhubRegister(String server, String user, String password, String url, PostType postType, SearchType searchType, List<Assembly> assemblies) {
        this.server = server;
        this.user = user;
        this.url = url;
        this.password = password;
        this.postType = postType;
        this.searchType = searchType;
        this.assemblies = assemblies;
    }

    /**
     * This method logs into the Registry using the supplied credentials. Generates auth token.
     *
     * @throws IOException Exception when reading/writing JSON to Registry.
     * @throws JSONException Exception when reading/writing JSON to Registry.
     * @throws HttpException Exception when reading/writing JSON to Registry.
     */
    public void login() throws IOException, JSONException, HttpException {
        System.setProperty("jsse.enableSNIExtension", "false");
        logger.info("Attempting to log into the registry.");
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(server.replaceFirst("https://", ""), AuthScope.ANY_PORT),
                new UsernamePasswordCredentials(user, password));
        try (CloseableHttpClient httpclient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .build()) {
            HttpGet httpget = new HttpGet(server + "/api/login");
            logger.info("Executing request " + httpget.getRequestLine());
            CloseableHttpResponse response = httpclient.execute(httpget);
            HttpEntity entity = response.getEntity();
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                try (InputStream inputStream = entity.getContent()) {
                    StringWriter writer = new StringWriter();
                    IOUtils.copy(inputStream, writer, StandardCharsets.UTF_8);
                    JSONObject myObject = new JSONObject(writer.toString());
                    myObject.get("auth_token");
                    this.authToken = myObject.getString("auth_token");
                    logger.info("Successfully obtained auth token.");
                }
            } else {
                logger.error("Error when logging in, status code: " + response.getStatusLine().getStatusCode());
                logger.error("Reason: " + response.getStatusLine().getReasonPhrase());
                throw new HttpException(response.getStatusLine().getReasonPhrase());
            }

        }
    }

    /**
     * This method logs out of the Registry, using the auth token generated after Login().
     * @throws IOException Exception when reading/writing JSON to Registry.
     * @throws HttpException Exception when reading/writing JSON to Registry.
     */
    public void logout() throws IOException, HttpException {
        logger.info("Attempting to log out");
        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build()) {
            HttpGet httpget = new HttpGet(server + "/api/logout");
            httpget.setHeader("user", user);
            httpget.setHeader("Auth-Token", authToken);
            CloseableHttpResponse response = httpclient.execute(httpget);
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                logger.info("Successfully logged out.");
            } else {
                logger.error("Error when logging out, status code: " + response.getStatusLine().getStatusCode());
                logger.error("Reason: " + response.getStatusLine().getReasonPhrase());
                throw new HttpException(response.getStatusLine().getReasonPhrase());
            }
        }
    }

    /**
     * This method posts the trackh hub to the Registry, using the auth token generated after Login().
     * @throws IOException Exception when reading/writing JSON to Registry.
     * @throws JSONException Exception when reading/writing JSON to Registry.
     * @throws HttpException Exception when reading/writing JSON to Registry.
     */
    public void postTrackhub() throws IOException, JSONException, HttpException {
        logger.info("Attempting to post track hub.");
        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build()) {
            HttpPost httppost = new HttpPost(server + "/api/trackhub");
            httppost.setHeader("User", user);
            httppost.setHeader("Auth-Token", authToken);
            JSONObject json = new JSONObject();
            json.put("url", url);
            json.put("type", postType);
            json.put("public", searchType.getValue());
            JSONObject jsonAssemblies = new JSONObject();
            if (assemblies.size()>0) {
                assemblies.forEach(assembly -> {
                    try {
                        jsonAssemblies.put(assembly.name(), assembly.getINSDC());
                    } catch (JSONException e) {
                        logger.error("Problem when adding assemblies. " + e);
                    }
                });
                json.put("assembliesNames", jsonAssemblies);
            } else {
                logger.error("Unable to read assemblies.");
            }
            StringEntity stringEntity = new StringEntity(json.toString());
            stringEntity.setContentType("application/json");
            httppost.setEntity(stringEntity);
            Arrays.stream(httppost.getAllHeaders()).forEach(header -> logger.info( header.getName() + " : " + header.getValue()));
            HttpResponse response = httpclient.execute(httppost);
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_CREATED) {
                logger.info("Successfully posted track hub to registry.");
                logger.debug("ReasonPhrase: " + response.getStatusLine().getReasonPhrase());
                logger.debug("Content: " + IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8));  // direct link to Genome Browser
            } else {
                logger.error("Error when posting track hub to registry, status code: " + response.getStatusLine().getStatusCode());
                logger.error("ReasonPhrase: " + response.getStatusLine().getReasonPhrase());
                logger.error("Content: " + IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8));
                throw new HttpException(response.getStatusLine().getReasonPhrase());
            }
        }
    }


  /**
   * Sets new server.
   *
   * @param server New value of server.
   */
  public void setServer(String server) {
    this.server = server;
  }

  /**
   * Sets new assemblies.
   *
   * @param assemblies New value of assemblies.
   */
  public void setAssemblies(List<Assembly> assemblies) {
    this.assemblies = assemblies;
  }

  /**
   * Gets postType.
   *
   * @return Value of postType.
   */
  public PostType getPostType() {
    return postType;
  }

  /**
   * Sets new postType.
   *
   * @param postType New value of postType.
   */
  public void setPostType(PostType postType) {
    this.postType = postType;
  }

  /**
   * Sets new password.
   *
   * @param password New value of password.
   */
  public void setPassword(String password) {
    this.password = password;
  }

  /**
   * Gets searchType.
   *
   * @return Value of searchType.
   */
  public SearchType getSearchType() {
    return searchType;
  }

  /**
   * Sets new authToken.
   *
   * @param authToken New value of authToken.
   */
  public void setAuthToken(String authToken) {
    this.authToken = authToken;
  }

  /**
   * Gets assemblies.
   *
   * @return Value of assemblies.
   */
  public List<Assembly> getAssemblies() {
    return assemblies;
  }

  /**
   * Sets new searchType.
   *
   * @param searchType New value of searchType.
   */
  public void setSearchType(SearchType searchType) {
    this.searchType = searchType;
  }

  /**
   * Gets password.
   *
   * @return Value of password.
   */
  public String getPassword() {
    return password;
  }

  /**
   * Gets url.
   *
   * @return Value of url.
   */
  public String getUrl() {
    return url;
  }

  /**
   * Gets server.
   *
   * @return Value of server.
   */
  public String getServer() {
    return server;
  }

  /**
   * Gets user.
   *
   * @return Value of user.
   */
  public String getUser() {
    return user;
  }

  /**
   * Sets new user.
   *
   * @param user New value of user.
   */
  public void setUser(String user) {
    this.user = user;
  }

  /**
   * Sets new url.
   *
   * @param url New value of url.
   */
  public void setUrl(String url) {
    this.url = url;
  }

  /**
   * Gets authToken.
   *
   * @return Value of authToken.
   */
  public String getAuthToken() {
    return authToken;
  }
}
