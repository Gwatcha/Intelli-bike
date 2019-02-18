package ca.ubc.zachrivard.self.test;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by zachr on 2018-04-03.
 */

public class ScriptExecutor extends AsyncTask<Void, Void, String> {


    String url;
    String filePath;
    Context context;
    static ArrayList<ScriptExecutor> list;
    static int i = 0;

    public ScriptExecutor(String url, String filePath, Context context) {
        this.url = url;
        this.context = context;
        this.filePath = filePath;
    }

    /**
     * Allows the sequentialization of
     * multipple AsyncTasks
     */
    public static void multiScript(ArrayList<ScriptExecutor> da){
        list = da;
    }

    /**
     * Run the current AsyncTask
     */
    public static void run(){
        ScriptExecutor s = list.get(i);

        s.execute();
    }

    HttpResponse response;
    @Override
    protected String doInBackground(Void... params) {
        HttpClient httpClient = new DefaultHttpClient();
        HttpContext localContext = new BasicHttpContext();
        HttpPost httpPost = new HttpPost(url);
        Log.d("URL", url);
        try {

            if(filePath != null){ //Add in the file
                MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
                entity.addPart("image", new FileBody(new File(filePath)));
                httpPost.setEntity(entity);
            }

            response = httpClient.execute(httpPost, localContext);

        } catch (IOException e) {
            e.printStackTrace();
        }
        if(response == null) return null;
        return response.getStatusLine().toString();
    }

    @Override
    protected void onPostExecute(String s) {
        super.onPostExecute(s);

        if(s.contains("200")){
            Toast.makeText(context, "Script executed successfully!", Toast.LENGTH_LONG).show();

            //If there are more tasks to run, run the next one
            if(++i < list.size()){
                run();
            }

        }else{
            Toast.makeText(context, "Error in execution - Please try in internet range", Toast.LENGTH_LONG).show();
        }


    }
}
