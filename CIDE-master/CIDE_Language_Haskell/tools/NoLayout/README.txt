Zwei M�glichkeiten der Anwendung von NoLayout:

1. Ausf�hrung im GHCI
NoLayout.hs in den GHCI laden (z.B. Shell-Kommando "ghci NoLayout.hs" oder im GHCI-Prompt ":l NoLayout.hs") und das Programm mit ":main EingabeDatei.hs AusgabeDatei.hs" ausf�hren

2. Kompilieren in eine ausf�hrbare Datei und dann Ausf�hren durch Shell-Kommando "nolayout EingabeDatei.hs AusgabeDatei.hs"
2.1 Kompilieren mit make
2.2 Manuelles Kompilieren mit dem Befehl "ghc -o nolayout NoLayout.hs -package haskell-src -main-is NoLayout.main"
