package me.kmathers.twitchannouncer;

public class YouTubeChannel {
    private String handle;
    private String registered_at;

    public YouTubeChannel() {}

    public YouTubeChannel(String handle, String registeredAt) {
        this.handle = handle;
        this.registered_at = registeredAt;
    }

    public String getHandle() {
        return handle;
    }

    public void setHandle(String handle) {
        this.handle = handle;
    }

    public String getRegisteredAt() {
        return registered_at;
    }

    public void setRegisteredAt(String registeredAt) {
        this.registered_at = registeredAt;
    }
}