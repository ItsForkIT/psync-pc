package bishakh.psync;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class WebServer extends NanoHTTPD {
    Controller controller;
    Logger logger;

    public WebServer(int port, Controller controller, Logger LoggerObj) {
        super(port);
        this.controller = controller;
        this.logger = LoggerObj;

    }


    @Override
    public Response serve(String uri, Method method,
                          Map<String, String> header,
                          Map<String, String> parameters,
                          Map<String, String> files) {
        // get filePath and Mime <FilePath, Mime>
        List<String> FileAndMime = new ArrayList<String>();
        FileAndMime = controller.urlResolver(uri);
        File f;
        String path = FileAndMime.get(0);
        logger.d("DEBUG", "WebServer: GET: " + path);
        if(!path.equals("")){
            String mimeType =  FileAndMime.get(1);

            if(!mimeType.equals("application/octet-stream")){

                try {
                    FileInputStream fileIS = new FileInputStream(path);
                    Response res = new Response(Response.Status.OK, mimeType,fileIS, fileIS.getChannel().size());
                    res.addHeader("Access-Control-Allow-Origin", "*");
                    return res;
                } catch (IOException e) {
                    //e.printStackTrace();
                    return createResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, new ByteArrayInputStream("404".getBytes(StandardCharsets.UTF_8)), "404".length());
                }
            }
            else {
                f = new File(path);
                return serveFile(uri, header, f, mimeType);
            }
        }
        else {
            return createResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, new ByteArrayInputStream("404".getBytes(StandardCharsets.UTF_8)), "404".length());
        }
    }

    //Announce that the file server accepts partial content requests
    private Response createResponse(Response.Status status, String mimeType,
                                    InputStream message, long messageSize) {
        Response res = new Response(status, mimeType, message, messageSize);
        res.addHeader("Accept-Ranges", "bytes");
        return res;
    }

    /**
     * Serves file from homeDir and its' subdirectories (only). Uses only URI,
     * ignores all headers and HTTP parameters.
     */
    private Response serveFile(String uri, Map<String, String> header,
                               File file, String mime) {
        Response res;
        logger.d("DEBUG", "Starting response");
        try {
            // Calculate etag
            String etag = Integer.toHexString((file.getAbsolutePath()
                    + file.lastModified() + "" + file.length()).hashCode());
            logger.d("DEBUG", "Etag calculated");

            // Support (simple) skipping:
            long startFrom = 0;
            long endAt = -1;
            String range = header.get("range");
            if (range != null) {
                if (range.startsWith("bytes=")) {
                    logger.d("DEBUG", "Range not null");
                    range = range.substring("bytes=".length());
                    int minus = range.indexOf('-');
                    try {
                        if (minus > 0) {
                            startFrom = Long.parseLong(range
                                    .substring(0, minus));
                            endAt = Long.parseLong(range.substring(minus + 1));
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            // Change return code and add Content-Range header when skipping is
            // requested
            long fileLen = file.length();
            if (range != null && startFrom >= 0) {
                if (startFrom >= fileLen) {
                    res = createResponse(Response.Status.RANGE_NOT_SATISFIABLE,
                            NanoHTTPD.MIME_PLAINTEXT, new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)), "".length());
                    res.addHeader("Content-Range", "bytes 0-0/" + fileLen);
                    res.addHeader("ETag", etag);
                    logger.d("DEBUG", "Range not satisfiable");
                } else {
                    if (endAt < 0) {
                        endAt = fileLen - 1;
                    }
                    long newLen = endAt - startFrom + 1;
                    if (newLen < 0) {
                        newLen = 0;
                    }

                    final long dataLen = newLen;
                    FileInputStream fis = new FileInputStream(file) {
                        @Override
                        public int available() throws IOException {
                            return (int) dataLen;
                        }
                    };
                    fis.skip(startFrom);

                    res = createResponse(Response.Status.PARTIAL_CONTENT, mime,
                            fis, file.length());
                    res.addHeader("Content-Length", "" + dataLen);
                    res.addHeader("Content-Range", "bytes " + startFrom + "-"
                            + endAt + "/" + fileLen);
                    res.addHeader("ETag", etag);
                    logger.d("DEBUG", "partial content");
                }
            } else {
                if (etag.equals(header.get("if-none-match"))) {
                    res = createResponse(Response.Status.NOT_MODIFIED, mime, new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)), "".length());
                } else {
                    res = createResponse(Response.Status.OK, mime,
                            new FileInputStream(file), fileLen);
                    res.addHeader("Content-Length", "" + fileLen);
                    res.addHeader("ETag", etag);
                    logger.d("DEBUG", "not modified");
                    res.addHeader("Content-Disposition", "attachment; filename=" + "\"" + file.getName()+"\"");
                }
            }
        } catch (IOException ioe) {
            res = createResponse(Response.Status.FORBIDDEN,
                    NanoHTTPD.MIME_PLAINTEXT, new ByteArrayInputStream("FORBIDDEN: Reading file failed.".getBytes(StandardCharsets.UTF_8)), "FORBIDDEN: Reading file failed.".length());
        }

        return res;
    }

}
