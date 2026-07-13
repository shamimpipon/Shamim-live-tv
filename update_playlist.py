import requests
import re
import os

def get_tokens():
    main_url = "http://redforce.live/"
    headers = {"User-Agent": "Mozilla/5.0"}
    response = requests.get(main_url, headers=headers)
    html = response.text
    
    # Extracting all channels
    regex = r'<li class="([^"]+)".*?stream=(\d+)".*?src="([^"]+)".*?alt="([^"]+)"'
    matches = re.findall(regex, html, re.S)
    
    playlist = "#EXTM3U\n"
    for group, stream_id, logo, name in matches:
        player_url = f"http://redforce.live/player.php?stream={stream_id}"
        p_res = requests.get(player_url, headers={"Referer": main_url, "User-Agent": "Mozilla/5.0"})
        m3u8_match = re.search(r'["\'](https?://[^"\']+\.m3u8[^"\']*)["\']', p_res.text)
        
        if m3u8_match:
            real_link = m3u8_match.group(1)
            group_name = group.replace(" All", "")
            logo_link = f"http://redforce.live/{logo}"
            playlist += f'#EXTINF:-1 tvg-logo="{logo_link}" group-title="{group_name}",{name}\n{real_link}\n'
            
    with open("playlist.m3u", "w", encoding="utf-8") as f:
        f.write(playlist)

if __name__ == "__main__":
    get_tokens()
