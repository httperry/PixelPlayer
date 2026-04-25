from ytmusicapi import YTMusic
import json
ytmusic = YTMusic('app/src/main/python/ytm_cookies.txt')
try:
    print("Trying to get streaming data via ytmusicapi...")
    song = ytmusic.get_song('c6rCRy6SrtU')
    if 'streamingData' in song:
        print("Success! found streaming data")
        formats = song['streamingData'].get('adaptiveFormats', [])
        for f in formats:
            if 'audio' in f.get('mimeType', ''):
                print(f"Format: {f.get('mimeType')} - Bitrate: {f.get('bitrate')}")
                if 'url' in f:
                    print("URL:", f['url'][:50], "...")
                elif 'signatureCipher' in f:
                    print("Has cipher, needs decryption")
    else:
        print("No streaming data found")
        print(json.dumps(song.get('playabilityStatus', {}), indent=2))
except Exception as e:
    print(f"Error: {e}")
