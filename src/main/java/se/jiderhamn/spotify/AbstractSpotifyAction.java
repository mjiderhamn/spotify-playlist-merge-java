package se.jiderhamn.spotify;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.exceptions.detailed.TooManyRequestsException;
import com.wrapper.spotify.exceptions.detailed.UnauthorizedException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.model_objects.specification.Playlist;
import com.wrapper.spotify.model_objects.specification.PlaylistTrack;
import com.wrapper.spotify.model_objects.specification.Track;
import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;
import com.wrapper.spotify.requests.data.playlists.AddTracksToPlaylistRequest;
import com.wrapper.spotify.requests.data.playlists.GetPlaylistsTracksRequest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;

import org.apache.commons.io.FileUtils;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

/**
 * @author Mattias Jiderhamn
 */
public abstract class AbstractSpotifyAction {
  
  final protected SpotifyApi client = SpotifyClient.getClient();
  
  private String userId = null;
  
  public static void main(AbstractSpotifyAction action, String[] args) throws IOException, SpotifyWebApiException {
    action.readAuth();
    try {
      action.perform();
    }
    catch (UnauthorizedException e) {
      System.err.println("Need to re-authenticate");
      action.authenticate();

      action.perform();
    }
  }
  
  private void readAuth() throws IOException {
    final Properties properties = readProps();
    client.setAccessToken(properties.getProperty("AccessToken"));
    client.setRefreshToken(properties.getProperty("RefreshToken"));
  }

  protected abstract void perform() throws IOException, SpotifyWebApiException;

  protected static void addTracksToPlaylist(String userId, Playlist playlist, List<Track> tracks) throws IOException, SpotifyWebApiException {
    final String[] uris = tracks.stream().map(Track::getUri).toArray(String[]::new);
    AddTracksToPlaylistRequest addTracksToPlaylistRequest = SpotifyClient.getClient()
              .addTracksToPlaylist(userId, playlist.getId(), uris)
              .position(0) // TODO The reason for reverse adding!
              .build();
    addTracksToPlaylistRequest.execute();
  }

  private void authenticate() throws IOException, SpotifyWebApiException {
    final AuthorizationCodeUriRequest authorizationCodeUriRequest = client.authorizationCodeUri()
              .scope("playlist-modify-private,playlist-read-private,playlist-read-collaborative,playlist-modify-public") // See https://beta.developer.spotify.com/documentation/general/guides/scopes/
              .show_dialog(true)
              .build();
    System.out.println("Open " + authorizationCodeUriRequest.execute());
    System.out.print("Authentication code: ");
    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    final String authCode = br.readLine();
    System.out.println("Received auth code: " + authCode);
    final AuthorizationCodeCredentials credentials = SpotifyClient.getCredentials(authCode);

    System.out.println("Expires in: " + credentials.getExpiresIn());
    System.out.println("Access token: " + credentials.getAccessToken());
    System.out.println("Refresh token: " + credentials.getRefreshToken());
    client.setAccessToken(credentials.getAccessToken());
    client.setRefreshToken(credentials.getRefreshToken());

    Properties props = new Properties();
    props.setProperty("AccessToken", credentials.getAccessToken());
    props.setProperty("RefreshToken", credentials.getRefreshToken());
    writeProps(props);
  }
  
  protected String getUserId() throws IOException, SpotifyWebApiException {
    if(userId == null) {
      userId = client.getCurrentUsersProfile().build().execute().getId();
    }
    return userId;
  }
  
  private static List<Track> getPlaylistTracks(String user, String playlist) throws IOException, SpotifyWebApiException {
    final SpotifyApi spotifyApi = SpotifyClient.getClient();
    final GetPlaylistsTracksRequest getPlaylist = spotifyApi.getPlaylistsTracks(user, playlist)
        .build();
    final Paging<PlaylistTrack> result = getPlaylist.execute();
    return stream(result.getItems()).map(PlaylistTrack::getTrack).collect(toList());
  }
  
  private static Properties readProps() throws IOException {
    File file = getPropsFile();
    Properties properties = new Properties();
    if(file.exists()) {
      System.out.println("Reading existing auth from " + file);
      properties.load(new FileReader(file));
    }
    return properties;
  }
  
  private static void writeProps(Properties properties) throws IOException {
    final File file = getPropsFile();
    System.out.println("Writing auth to " + file);
    properties.store(new FileWriter(file), AbstractSpotifyAction.class.getName());
  }

  private static File getPropsFile() {
    return new File(FileUtils.getTempDirectory(), AbstractSpotifyAction.class.getName() + ".auth");
  }
  
  protected <T> T rateLimited(Callable<T> callable) {
    try {
      return callable.call();
    }
    catch (TooManyRequestsException e) {
      System.out.print('.');
      try {
        Thread.sleep(e.getRetryAfter() * 1000 + 100);
      }
      catch (InterruptedException ignored) { }
      return rateLimited(callable);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}