# commentmvc

The ReactJS comment tutorial app using Om in a Pedestal style with the [Rohm library] (https://github.com/craygo/rohm)

## Using it

Follow the Om and Rohm installation instructions first, then 

```bash
lein cljsbuild once dev
```

And run a server e.g. with

```bash
python -m SimpleHTTPServer
```
Open your browser at http://localhost:8000

You can also interact with the app in a Clojurescript Repl with

```bash
lein trampoline cljsbuild repl-listen
```
Refresh the browser and in the Cljs repl
```cljs
(in-ns 'commentmvc.core)
(rohm/put-msg :add [:comments] {:author "James Joyce" :text "Mistakes are the portals of discovery."})
```
