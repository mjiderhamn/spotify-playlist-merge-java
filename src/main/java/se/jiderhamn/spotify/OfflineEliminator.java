package se.jiderhamn.spotify;

import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.model_objects.specification.PlaylistSimplified;
import com.wrapper.spotify.model_objects.specification.PlaylistTrack;
import com.wrapper.spotify.model_objects.specification.Track;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

/**
 * @author Mattias Jiderhamn
 */
public class OfflineEliminator extends AbstractSpotifyAction {
  
  // private final static String LOCAL_PLAYLIST_SUFFIX = " - LOCAL";
  
  private final static int TRACKS_PER_PAGE = 100;
  
  // private final Map<String, PlaylistSimplified> localPlaylists = new HashMap<>();

  public static void main(String[] args) throws IOException, SpotifyWebApiException {
    main(new OfflineEliminator(), args);
  }

  @Override
  protected void perform() throws IOException, SpotifyWebApiException {
    final List<PlaylistSimplified> allUsersPlaylists = getAllUsersPlaylists();
    /*
    for(PlaylistSimplified playlist : allUsersPlaylists) {
      if(playlist.getName().endsWith(LOCAL_PLAYLIST_SUFFIX)) {
        localPlaylists.put(playlist.getName(), playlist);
      }
    }
    */
    
    final String userId = getUserId();
    for(PlaylistSimplified playlist : allUsersPlaylists) {
      if(userId.equals(playlist.getOwner().getId()) /* && ! localPlaylists.containsKey(playlist.getName()) */) {
        processPlaylist(playlist);
      }
    }

//    final Playlist temp = client.getPlaylist("58eA4SpDkkXWjX0grV9F5H").build().execute();
//    processPlaylist(temp);

  }

  private void processPlaylist(PlaylistSimplified playlist) throws IOException, SpotifyWebApiException {
    System.out.print("Playlist '" + playlist.getName() + "': ");
    
    final Map<String, String> replacements = new LinkedHashMap<>(); // local URI => online URI (retain order)
    final Set<String> onlineTracks = new HashSet<>(); // To avoid duplicates
    
    int trackPosition = 0;
    int tracksProcessed = 0;
    int totalNoOfTracks;
    do {
      final Paging<PlaylistTrack> page = client.getPlaylistsTracks(playlist.getId()).offset(tracksProcessed).limit(TRACKS_PER_PAGE).build().execute();
      totalNoOfTracks = page.getTotal();
      final PlaylistTrack[] tracks = page.getItems();
      for(PlaylistTrack playlistTrack : tracks) {
        if(playlistTrack.getIsLocal() != null && playlistTrack.getIsLocal()) {
          final Track localTrack = playlistTrack.getTrack();
          final List<Track> foundTracks = searchTrack(localTrack);
          if(foundTracks.size() == 1) { // Exactly one match - replace with online version
            final Track onlineTrack = foundTracks.get(0);
            replacements.put(localTrack.getUri(), onlineTrack.getUri());
            
//            System.out.println("About to replace " + localTrack.getUri() + " with " + onlineTrack.getUri() + " in " + playlist.getName());
//            client.addTracksToPlaylist(playlist.getId(), new String[]{onlineTrack.getUri()}).build().execute();
//            final JsonArray tracksToRemove = new JsonArray();
//            final JsonObject trackToRemove = new JsonObject();
//            // trackToRemove.addProperty("uri", localTrack.getUri());
//            trackToRemove.addProperty("uri", "*");
//            final JsonArray positions = new JsonArray();
//            positions.add(trackPosition);
//            trackToRemove.add("positions", positions);
//            tracksToRemove.add(trackToRemove);
//            client.removeTracksFromPlaylist(playlist.getId(), null).snapshotId(playlist.getSnapshotId()).build().execute();
            // Removal does not currently work!
//            System.exit(0);  
          }
        }
        else { // Already online track
          onlineTracks.add(playlistTrack.getTrack().getUri());
        }
        trackPosition++;
      }
      tracksProcessed += page.getItems().length;
      // System.out.println("Processed " + tracksProcessed + " of " + totalNoOfTracks + " in playlist " + playlist.getName());
    } while(tracksProcessed < totalNoOfTracks);
    
    if(trackPosition != tracksProcessed)
      throw new IllegalStateException();
    
    for(String localUri : new HashSet<>(replacements.keySet())) {
      if(onlineTracks.contains(replacements.get(localUri))) { // Online track already added, avoid duplicate
        replacements.remove(localUri);
      }
    }

    if(! replacements.isEmpty()) {
      System.out.println(replacements.size() + " tracks can be converted to online");
      client.addTracksToPlaylist(playlist.getId(), replacements.values().toArray(new String[0])).build().execute();
    }
    else {
      System.out.println("Nothing to replace");
    }
    
//    if(replacedTracks > 0) {
//      System.out.println("Replacing " + replacedTracks + " local tracks with online tracks in '" + playlist.getName() + "'");
//      System.out.println("Result: " + client.replacePlaylistsTracks(playlist.getId(), uris.toArray(new String[0])).build().execute());
//    }
//              client.replacePlaylistsTracks();
  }
  
  private List<Track> searchTrack(Track criteria) throws IOException, SpotifyWebApiException {
    String queryString = "artist:\"" + criteria.getArtists()[0].getName()/*.replace(' ', '+')*/ + 
                "\" album:\"" + criteria.getAlbum().getName()/*.replace(' ', '+')*/ +
                "\" track:\"" + criteria.getName()/*.replace(' ', '+')*/ + "\""; 
    
    final Paging<Track> searchPage = client.searchTracks(queryString).build().execute();

    // System.out.println("'" + criteria.getName() + "' is local. Searching using " + queryString + " resulted in " + searchPage.getTotal());
    
    return searchPage.getTotal() == 0 ? emptyList() : asList(searchPage.getItems());
  }

  private List<PlaylistSimplified> getAllUsersPlaylists() throws IOException, SpotifyWebApiException {
    final List<PlaylistSimplified> allPlaylists = new ArrayList<>();
    final int pageSize = 50;
    int offset = 0;
    int total;
    do {
      final Paging<PlaylistSimplified> page = client.getListOfCurrentUsersPlaylists().limit(pageSize).offset(offset).build().execute();
      total = page.getTotal();
      offset += pageSize;
      
      final PlaylistSimplified[] items = page.getItems();
      Arrays.stream(items).filter(Objects::nonNull).forEach(allPlaylists::add);
      System.out.println("Fetched " + allPlaylists.size() + " out of " + total + " playlists");
    } while(allPlaylists.size() < total);
    return allPlaylists;
  }
}