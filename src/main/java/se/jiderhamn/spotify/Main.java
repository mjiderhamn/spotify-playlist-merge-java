package se.jiderhamn.spotify;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.model_objects.specification.Playlist;
import com.wrapper.spotify.model_objects.specification.PlaylistTrack;
import com.wrapper.spotify.model_objects.specification.Track;
import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;
import com.wrapper.spotify.requests.data.playlists.GetPlaylistsTracksRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

import org.apache.commons.collections4.ListUtils;

import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * @author Mattias Jiderhamn
 */
public class Main extends AbstractSpotifyAction {
  
  private static final String RESULT_USER_ID = "mattiasj78";

  private static Map<String, Map<String, List<String>>> typesOfPlaylists = new HashMap<>();
  
  private static String[] CYCLE = {"Bugg", "Bugg", "Fox", "Fox"};

  @SuppressWarnings("unused")
  private static final String SILENCE_10s = "5XSKC4d0y0DfcGbvDOiL93";
  
  private static final String SILENCE_17s = "4ZZgjJUKOG0DarLHTmPnP8";
  
  @SuppressWarnings("unused")
  private static final String SILENCE_30s = "3E3Kz6ZrphV9lRKhQjZAkl";

  /** Track to insert between each new type of track in the cycle */
  private static String[] BETWEEN_TYPES = {}; // TODO 5 seconds
  
  private static String[] BETWEEN_CYCLES = {SILENCE_17s};
  
  static {
    typesOfPlaylists
        .computeIfAbsent("Bugg", k -> new HashMap<>())
          .computeIfAbsent("mattiasj78", k -> new ArrayList<>())
            .add("6lIfmLTd4BRjrqWYjVKEwA");
    typesOfPlaylists.computeIfAbsent("Fox", k -> new HashMap<>())
        .computeIfAbsent("mattiasj78", k -> new ArrayList<>())
          .addAll(asList("4zzs5Px2dNbt9gOCg2lZU8", "09NNLPLDSoqcHVHOwcB58r"));
  }
  
  public static void main(String[] args) throws IOException, SpotifyWebApiException {
    main(new Main(), args);
  }

  protected void perform() throws IOException, SpotifyWebApiException {
    final Map<String, List<Track>> tracksPerType = sortTracks();
    for(Map.Entry<String, List<Track>> entry : tracksPerType.entrySet()) {
      final List<Track> tracks = entry.getValue();
      final Duration totalTime = Duration.of(tracks.stream().mapToInt(Track::getDurationMs).sum(), ChronoUnit.MILLIS);
      final long hours = totalTime.toHours();
      System.err.println("Found " + tracks.size() + " tracks of type '" + entry.getKey() + "'. Total time " +
          hours + " h " + totalTime.minus(hours, ChronoUnit.HOURS).toMinutes() + " min");
    }

    System.out.println();
    System.out.println("---------------------------");
    System.out.println();

    // Merge
    final List<Track> result = merge(tracksPerType);

    System.out.println();
    System.out.println("---------------------------");
    System.out.println();

    // Print remainders
    for(String type : tracksPerType.keySet()) {
      final List<Track> tracksOfType = tracksPerType.get(type);
      if(! tracksOfType.isEmpty()) {
        System.out.println("Unused tracks of type " + type + "(" + tracksOfType.size() + "):");
        tracksOfType.forEach(track -> System.out.println("  " + track.getName()));
      }
    }

    // Store playlist
    final String name = stream(CYCLE).collect(joining(", ")) + " @ " + LocalDateTime.now().toString();
    final SpotifyApi spotifyApi = SpotifyClient.getClient();
    Playlist playlist = spotifyApi.createPlaylist(RESULT_USER_ID, name)
              .collaborative(false)
              .public_(false)
              .description("Generated " + name)
              .build()
              .execute();

    // Cannot do all at once https://github.com/thelinmichael/spotify-web-api-node/issues/82
    // Seems that tracks with a request are kept in order, but consecutive request end up *before* earlier requests
    final List<List<Track>> partitions = new ArrayList<>(ListUtils.partition(result, 50));
    Collections.reverse(partitions);
    for(List<Track> partition : partitions) {
      addTracksToPlaylist(RESULT_USER_ID, playlist, partition);
    }
    
    // getTempo();
  }

  private static List<Track> merge(Map<String, List<Track>> tracksPerType) {
    final SpotifyApi client = SpotifyClient.getClient();
    final Function<String, Track> trackIdToTrack = id -> {
      try {
        return client.getTrack(id).build().execute();
      }
      catch (IOException | SpotifyWebApiException e) {
        throw new RuntimeException();
      }
    };

    final Random rand = new Random(System.currentTimeMillis());
    final List<Track> output = new ArrayList<>();
    
    final Map<String, Track> idToTrack = new HashMap<>();

    while(true) {
      String previousType = null;
      for(String type : CYCLE) {
        if(previousType != null && ! previousType.equals(type)) {
          for(String trackId : BETWEEN_TYPES) {
            final Track track = idToTrack.computeIfAbsent(trackId, trackIdToTrack);
            System.out.println(previousType + " -> " + type + ": " + track.getName());
            output.add(track);
          }
        }
        previousType = type;
        
        final List<Track> tracksOfType = tracksPerType.get(type);
        if(tracksOfType == null)
          throw new RuntimeException("No tracks of type " + type);
        
        // for(int c = 0; c < CONSECUTIVE_TRACKS_PER_TYPE; c++) {
          if(tracksOfType.isEmpty()) {
            System.err.println("No more tracks of type " + type);
            return output;
          }
            
          final int i = rand.nextInt(tracksOfType.size());
          final Track track = tracksOfType.remove(i);
          System.out.println(type + ": " + track.getName());
          output.add(track); // Remove to avoid duplicates
        // }
      }
      for(String trackId : BETWEEN_CYCLES) {
        final Track track = idToTrack.computeIfAbsent(trackId, trackIdToTrack);
        System.out.println("Between cycles: " + track.getName());
        output.add(track);
      }
    }
  }

  /*
  private static void getTempo() {
  GetAudioFeaturesForSeveralTracksRequest getAudioFeaturesForSeveralTracksRequest = spotifyApi
            .getAudioFeaturesForSeveralTracks(tracks.keySet().toArray(new String[tracks.size()]))
            .build();
  final AudioFeatures[] audioFeatures = getAudioFeaturesForSeveralTracksRequest.execute();
  for(AudioFeatures audioFeature : audioFeatures) {
    System.out.println(tracks.get(audioFeature.getId()) + " has tempo " + audioFeature.getTempo());
  }
  }
  */

  private static Map<String, List<Track>> sortTracks() {
    final Map<String, List<Track>> tracksPerType = new HashMap<>();

    for(String type : typesOfPlaylists.keySet()) {
      final List<Track> tracks = tracksPerType.computeIfAbsent(type, k -> new ArrayList<>());

      final Map<String, List<String>> userPlaylists = typesOfPlaylists.get(type);
      for(String user : userPlaylists.keySet()) {
        userPlaylists.get(user).forEach(playlist -> {
              try {
                tracks.addAll(getPlaylistTracks(user, playlist));
              }
              catch (IOException | SpotifyWebApiException e) {
                throw new RuntimeException(e);
              }
            });
      } 
    }
    return tracksPerType;
  }

  private static void authenticate(SpotifyApi client) throws IOException, SpotifyWebApiException {
    final AuthorizationCodeUriRequest authorizationCodeUriRequest = client.authorizationCodeUri()
              .scope("playlist-modify-private,playlist-read-private,playlist-read-collaborative") // See https://beta.developer.spotify.com/documentation/general/guides/scopes/
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
  }
  
  private static List<Track> getPlaylistTracks(String user, String playlist) throws IOException, SpotifyWebApiException {
    final SpotifyApi spotifyApi = SpotifyClient.getClient();
    final GetPlaylistsTracksRequest getPlaylist = spotifyApi.getPlaylistsTracks(user, playlist)
        .build();
    final Paging<PlaylistTrack> result = getPlaylist.execute();
    return stream(result.getItems()).map(PlaylistTrack::getTrack).collect(toList());
  }
}