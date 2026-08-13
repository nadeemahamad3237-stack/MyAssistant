package com.myassistant.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    LinearLayout messages, chatList, sidebar;
    EditText input;
    TextView title, status, settings;
    ScrollView scroll;
    SharedPreferences sp;
    JSONArray chats;
    String currentId = "";
    TextToSpeech tts;
    SpeechRecognizer recognizer;

    int purple = Color.rgb(145, 124, 255);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        messages=findViewById(R.id.messages); chatList=findViewById(R.id.chatList);
        sidebar=findViewById(R.id.sidebar); input=findViewById(R.id.input);
        title=findViewById(R.id.title); status=findViewById(R.id.status);
        scroll=findViewById(R.id.scroll); settings=findViewById(R.id.settings);

        sp=getSharedPreferences("memory",MODE_PRIVATE);
        loadChats();
        tts=new TextToSpeech(this, x->{});
        findViewById(R.id.menu).setOnClickListener(v->toggleSidebar());
        findViewById(R.id.newChat).setOnClickListener(v->{newChat(); toggleSidebar();});
        findViewById(R.id.send).setOnClickListener(v->send());
        findViewById(R.id.more).setOnClickListener(v->showSettings());
        settings.setOnClickListener(v->showSettings());
        findViewById(R.id.mic).setOnClickListener(v->startVoice());
        input.setOnEditorActionListener((v,a,e)->{send();return true;});
        renderCurrent();
    }

    void loadChats(){
        try { chats=new JSONArray(sp.getString("chats","[]")); }
        catch(Exception e){ chats=new JSONArray(); }
        if(chats.length()==0) newChat();
        else currentId=chats.optJSONObject(0).optString("id");
    }

    void persist(){ sp.edit().putString("chats",chats.toString()).apply(); }

    void newChat(){
        JSONObject c=new JSONObject();
        try {
            currentId=UUID.randomUUID().toString();
            c.put("id",currentId); c.put("title","New chat"); c.put("messages",new JSONArray());
            chats.put(0,c); persist(); renderCurrent();
        } catch(Exception ignored){}
    }

    JSONObject current(){
        for(int i=0;i<chats.length();i++) if(chats.optJSONObject(i).optString("id").equals(currentId)) return chats.optJSONObject(i);
        return chats.optJSONObject(0);
    }

    void renderCurrent(){
        JSONObject c=current(); if(c==null)return;
        title.setText(c.optString("title","New chat")); messages.removeAllViews();
        JSONArray a=c.optJSONArray("messages"); if(a!=null) for(int i=0;i<a.length();i++){
            JSONObject m=a.optJSONObject(i); addBubble(m.optString("role"),m.optString("content"));
        }
        renderSidebar();
        scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
    }

    void renderSidebar(){
        chatList.removeAllViews();
        for(int i=0;i<chats.length();i++){
            JSONObject c=chats.optJSONObject(i);
            TextView row=new TextView(this); row.setText(c.optString("title","New chat")); row.setTextColor(Color.rgb(220,220,228));
            row.setTextSize(17); row.setPadding(12,18,8,18);
            final String id=c.optString("id");
            row.setOnClickListener(v->{currentId=id; renderCurrent(); toggleSidebar();});
            row.setOnLongClickListener(v->{chatMenu(id);return true;});
            chatList.addView(row);
        }
    }

    void chatMenu(String id){
        final String[] ops={"Rename","Delete"};
        new AlertDialog.Builder(this).setItems(ops,(d,w)->{
            if(w==0) renameChat(id); else deleteChat(id);
        }).show();
    }

    void renameChat(String id){
        EditText e=new EditText(this); e.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle("Rename chat").setView(e).setPositiveButton("Save",(d,w)->{
            for(int i=0;i<chats.length();i++) if(chats.optJSONObject(i).optString("id").equals(id)) try{chats.optJSONObject(i).put("title",e.getText().toString().trim());}catch(Exception ignored){}
            persist(); renderCurrent();
        }).setNegativeButton("Cancel",null).show();
    }

    void deleteChat(String id){
        if(chats.length()<=1){Toast.makeText(this,"At least one chat rahega.",Toast.LENGTH_SHORT).show();return;}
        JSONArray n=new JSONArray();
        for(int i=0;i<chats.length();i++) if(!chats.optJSONObject(i).optString("id").equals(id)) n.put(chats.optJSONObject(i));
        chats=n; if(currentId.equals(id)) currentId=chats.optJSONObject(0).optString("id");
        persist(); renderCurrent();
    }

    void addBubble(String role,String text){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        TextView who=new TextView(this); who.setText(role.equals("user")?"You":"MyAssistant");
        who.setTextColor(role.equals("user")?purple:Color.rgb(150,153,165)); who.setTextSize(15); who.setTypeface(null,1);
        TextView body=new TextView(this); body.setText(text); body.setTextColor(Color.WHITE); body.setTextSize(18);
        body.setPadding(18,14,18,14); body.setBackgroundResource(role.equals("user")?R.drawable.bg_message_user:R.drawable.bg_message_ai);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2); bp.setMargins(0,6,0,18);
        box.addView(who); box.addView(body,bp); messages.addView(box);
    }

    void send(){
        String text=input.getText().toString().trim(); if(text.isEmpty())return;
        input.setText(""); addMessage("user",text); status.setText("●  Thinking…");
        new Thread(()->{
            String reply=callAI(text);
            runOnUiThread(()->{ addMessage("assistant",reply); status.setText("●  Ready"); });
        }).start();
    }

    void addMessage(String role,String text){
        JSONObject c=current(); if(c==null)return;
        try{
            JSONArray a=c.optJSONArray("messages"); if(a==null){a=new JSONArray();c.put("messages",a);}
            JSONObject m=new JSONObject();m.put("role",role);m.put("content",text);a.put(m);
            if(role.equals("user") && c.optString("title").equals("New chat")){
                String t=text.replaceAll("\\s+"," ").trim(); c.put("title",t.length()>28?t.substring(0,28)+"…":t);
            }
            persist(); addBubble(role,text); renderSidebar(); scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
        }catch(Exception ignored){}
    }

    String callAI(String user){
        String key=sp.getString("groq_key","");
        if(key.isEmpty()) return "Bhai, AI key abhi set nahi hai. ☺️ Menu → AI Settings me apni Groq API key add kar do, phir main properly tumhare saath chat karunga.";
        try{
            JSONObject body=new JSONObject(); body.put("model","llama-3.3-70b-versatile"); body.put("max_completion_tokens",700);
            JSONArray ms=new JSONArray();
            JSONObject sys=new JSONObject();sys.put("role","system");sys.put("content",
                "You are MyAssistant, a warm, intelligent personal AI friend. "+
                "Speak naturally in Hindi, Hinglish or English matching the user's language. "+
                "Never sound like a canned FAQ or customer-support bot. Use the current conversation context. "+
                "Be concise for simple chat, thoughtful for complex questions. "+
                "Remember facts the user tells you from the supplied history. "+
                "For coding requests, actually provide useful code and explain it step by step when appropriate. "+
                "Do not claim you performed actions you cannot perform. "+
                "Do not control the phone or apps. You are a chat and coding assistant.");
            ms.put(sys);
            JSONArray hist=current().optJSONArray("messages");
            if(hist!=null){
                int start=Math.max(0,hist.length()-16);
                for(int i=start;i<hist.length();i++) ms.put(hist.optJSONObject(i));
            }
            body.put("messages",ms);
            HttpURLConnection con=(HttpURLConnection)new URL("https://api.groq.com/openai/v1/chat/completions").openConnection();
            con.setRequestMethod("POST");con.setConnectTimeout(15000);con.setReadTimeout(30000);
            con.setDoOutput(true);con.setRequestProperty("Authorization","Bearer "+key);con.setRequestProperty("Content-Type","application/json");
            try(OutputStream os=con.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}
            InputStream is=con.getResponseCode()<400?con.getInputStream():con.getErrorStream();
            String out=new String(is.readAllBytes(),StandardCharsets.UTF_8);
            JSONObject r=new JSONObject(out);
            if(!r.has("choices")) return "AI se response nahi mila. API key ya Groq response check karte hain.";
            return r.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();
        }catch(Exception e){return "Bhai, AI connection me problem aa gayi. Thodi der baad try karte hain.";}
    }

    void toggleSidebar(){sidebar.setVisibility(sidebar.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE); if(sidebar.getVisibility()==View.VISIBLE)renderSidebar();}

    void showSettings(){
        LinearLayout l=new LinearLayout(this);l.setPadding(35,10,35,0);l.setOrientation(LinearLayout.VERTICAL);
        EditText key=new EditText(this);key.setHint("Groq API key");key.setSingleLine(true);key.setText(sp.getString("groq_key",""));
        l.addView(key);
        new AlertDialog.Builder(this).setTitle("AI Settings").setMessage("API key device par locally save hogi. GitHub me commit nahi hoti.").setView(l)
            .setPositiveButton("Save",(d,w)->sp.edit().putString("groq_key",key.getText().toString().trim()).apply())
            .setNegativeButton("Cancel",null).show();
    }

    void startVoice(){
        if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},44); return;
        }
        if(!SpeechRecognizer.isRecognitionAvailable(this)){Toast.makeText(this,"Voice input available nahi hai.",Toast.LENGTH_SHORT).show();return;}
        recognizer=SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener(){
            public void onReadyForSpeech(Bundle b){} public void onBeginningOfSpeech(){} public void onRmsChanged(float v){}
            public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){}
            public void onError(int e){status.setText("●  Ready");}
            public void onResults(Bundle b){ArrayList<String>x=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(x!=null&&!x.isEmpty()){input.setText(x.get(0));send();}}
            public void onPartialResults(Bundle b){} public void onEvent(int a,Bundle b){}
        });
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT,"Bolo…");recognizer.startListening(i);status.setText("●  Listening…");
    }

    @Override protected void onDestroy(){if(recognizer!=null)recognizer.destroy();if(tts!=null)tts.shutdown();super.onDestroy();}
}
