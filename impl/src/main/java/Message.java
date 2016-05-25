import com.google.gson.Gson;

import java.nio.charset.Charset;

/**
 * Created by brandon on 5/24/16.
 */
public abstract class Message {

    public byte[] serialize() {
        Gson gson = new Gson();
        return gson.toJson(this).getBytes(Charset.defaultCharset());
    }
}
