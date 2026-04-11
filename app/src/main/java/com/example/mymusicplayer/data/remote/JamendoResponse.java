package com.example.mymusicplayer.data.remote;

import com.example.mymusicplayer.data.local.entity.Track;

import java.util.List;

public class JamendoResponse {

    private List<Track> results;

    public List<Track> getResults() {
        return results;
    }

    public void setResults(List<Track> results) {
        this.results = results;
    }
}
