package se.jiderhamn.spotify;

import java.io.IOException;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;

/**
 * @author Mattias Jiderhamn
 */
public class SpotifyClient {

  private static final String CLIENT_ID = "9f2f7495b9d74d8b805c3ce656967c2e";
  private static final SpotifyApi SPOTIFY_API = new SpotifyApi.Builder()
      .setClientId(CLIENT_ID)
      .setClientSecret("c69ccd9be4a54dd5bbfcfdee7cce4d90")
      .setRedirectUri(SpotifyHttpManager.makeUri("http://jiderhamn.se/spotify-redirect"))
      .build();

  public static SpotifyApi getClient() {
    return SPOTIFY_API;
  }

  public static AuthorizationCodeCredentials getCredentials(String authCode) throws IOException, SpotifyWebApiException {
    return getClient().authorizationCode(authCode).build().execute();
  }
}