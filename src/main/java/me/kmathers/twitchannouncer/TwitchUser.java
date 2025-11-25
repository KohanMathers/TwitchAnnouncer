package me.kmathers.twitchannouncer;

// TwitchUser.java
public class TwitchUser {
    private String username;
    private String display_name;
    private String profile_image_url;
    private String created_at;

    public TwitchUser() {}

    public TwitchUser(String username, String displayName, String profileImageUrl, String createdAt) {
        this.username = username;
        this.display_name = displayName;
        this.profile_image_url = profileImageUrl;
        this.created_at = createdAt;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return display_name;
    }

    public void setDisplayName(String displayName) {
        this.display_name = displayName;
    }

    public String getProfileImageUrl() {
        return profile_image_url;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profile_image_url = profileImageUrl;
    }

    public String getCreatedAt() {
        return created_at;
    }

    public void setCreatedAt(String createdAt) {
        this.created_at = createdAt;
    }
}

// YouTubeChannel.java in the same package