# FarGoneSpy

This is a slightly enhanced version of https://github.com/gonespy/bstormps3 which changes the following:
 - Supports server listing: For supported games, users connected to the same fargonespy instance can see and join
   each other's games. 
 - Works on modern versions of Java (the original version only worked with Java 8 JRE)
 - Fixes some performance issues that caused the server to unnecessarily use a lot of CPU.

# Usage

The instructions are the same as for the original version at https://github.com/gonespy/bstormps3 so please
refer to the instructions there.

# Tested games

| Game                    | Works? | Status                                                                                                                                                          |
|-------------------------|--------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Dungeon Defenders       | Yes    | When connected to the same instance, players can join each other's lobbies. When connected to separate instances, players can be manually invited to the lobby. |
| Joe Danger 2: The Movie | No     | No. Login does not work.                                                                                                                                        |
| Unreal Tournament 3     | Yes    | When connected to the same instance, players can join each other's lobbies.                                                                                     |


# Credits

Most of the credit goes to the original author of https://github.com/gonespy/bstormps3

https://github.com/AdmiralCurtiss/nintendo_dwc_emulator was also very helpful in figuring out how certain aspects
of the GameSpy protocol work.