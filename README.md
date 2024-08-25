# FarGoneSpy

This is a slightly enhanced version of https://github.com/gonespy/bstormps3 which changes the following:

- Supports server listing: For supported games, users connected to the same fargonespy instance can see and join
  each other's games.
- Works on modern versions of Java (the original version only worked with Java 8 JRE)
- Fixes some performance issues that caused the server to unnecessarily use a lot of CPU.

# Usage

## Windows

First, make sure you have a recent version of Java installed from https://www.oracle.com/java/technologies/downloads/

Download the latest fargonespy jar & dns exe files
from https://gitlab.com/fargonespy1/fargonespy/-/tree/master/release?ref_type=heads

First run `dns-<version>.exe` which will start a simple DNS server that will instruct your PS3 to send gamespy traffic to
the fargonespy server that will be started in the next step.

Then double-click on the downloaded `fargonespy-<version>.jar` file which, after a couple of seconds, should open a web
browser with information about configuring your PS3 DNS settings.

# Tested games

| Game                      | Works? | Status                                                                                                                                                          |
|---------------------------|--------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Dungeon Defenders         | Yes    | When connected to the same instance, players can join each other's lobbies. When connected to separate instances, players can be manually invited to the lobby. |
| Guardians of Middle-earth | No     | Game crashes after attempting matchmaking, likely related to the fact that login doesn't work.                                                                  |
| Joe Danger 2: The Movie   | No     | Login does not work.                                                                                                                                            |
| Mortal Kombat 9           | No     | Login doesn't work. Tested pre-patched version.                                                                                                                 |
| Unreal Tournament 3       | Yes    | When connected to the same instance, players can join each other's lobbies.                                                                                     |

# Credits

Most of the credit goes to the original author of https://github.com/gonespy/bstormps3

https://github.com/AdmiralCurtiss/nintendo_dwc_emulator was also very helpful in figuring out how certain aspects
of the GameSpy protocol work.