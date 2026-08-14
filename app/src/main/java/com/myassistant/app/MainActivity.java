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
    TextView title, status;
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
        scroll=findViewById(R.id.scroll);

        sp=getSharedPreferences("memory",MODE_PRIVATE);
        loadChats();
        tts=new TextToSpeech(this, x->{});
        findViewById(R.id.menu).setOnClickListener(v->toggleSidebar());
        findViewById(R.id.sidebarClose).setOnClickListener(v->closeSidebar());
        findViewById(R.id.sidebarScrim).setOnClickListener(v->closeSidebar());
        findViewById(R.id.newChat).setOnClickListener(v->{newChat(); closeSidebar();});
        findViewById(R.id.send).setOnClickListener(v->send());
        findViewById(R.id.more).setOnClickListener(v->Toast.makeText(this,"API key build environment se configured hai.",Toast.LENGTH_SHORT).show());
        findViewById(R.id.mic).setOnClickListener(v->startVoice());
        input.setOnEditorActionListener((v,a,e)->{send();return true;});
        renderCurrent();
    }

    void loadChats(){
        try { chats=new JSONArray(sp.getString("chats","[]")); }
        catch(Exception e){ chats=new JSONArray(); }

        if(chats.length()==0) {
            newChat();
            return;
        }

        String savedId=sp.getString("current_chat_id","");
        boolean found=false;

        for(int i=0;i<chats.length();i++){
            if(chats.optJSONObject(i).optString("id").equals(savedId)){
                currentId=savedId;
                found=true;
                break;
            }
        }

        if(!found) currentId=chats.optJSONObject(0).optString("id");
    }

    void persist(){
        sp.edit()
            .putString("chats",chats.toString())
            .putString("current_chat_id",currentId)
            .commit();
    }

    void newChat(){
        try {
            // Never delete the existing conversation.
            // Save the current conversation before switching to a new one.
            persist();

            JSONObject c=new JSONObject();
            currentId=UUID.randomUUID().toString();
            c.put("id",currentId);
            c.put("title","New chat");
            c.put("messages",new JSONArray());

            chats.put(0,c);
            persist();
            renderCurrent();
        } catch(Exception e){
            Toast.makeText(this,"New chat create nahi ho paya.",Toast.LENGTH_SHORT).show();
        }
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
            row.setOnClickListener(v->{
                currentId=id;
                persist();
                renderCurrent();
                closeSidebar();
            });
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
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        TextView who=new TextView(this);
        who.setText(role.equals("user")?"You":"MyAssistant");
        who.setTextColor(role.equals("user")?purple:Color.rgb(150,153,165));
        who.setTextSize(15);
        who.setTypeface(null,1);
        box.addView(who);

        int pos=0;

        while(pos<text.length()){
            int open=text.indexOf("```",pos);

            if(open<0){
                addSelectableText(box,text.substring(pos),role);
                break;
            }

            if(open>pos){
                addSelectableText(box,text.substring(pos,open),role);
            }

            int codeStart=text.indexOf('\n',open+3);
            if(codeStart<0) codeStart=open+3;
            else codeStart++;

            int close=text.indexOf("```",codeStart);

            if(close<0){
                addCodeBlock(box,text.substring(codeStart));
                break;
            }

            addCodeBlock(box,text.substring(codeStart,close));
            pos=close+3;
        }

        LinearLayout.LayoutParams bp=
            new LinearLayout.LayoutParams(-1,-2);
        bp.setMargins(0,6,0,18);
        messages.addView(box,bp);
    }

    void addSelectableText(LinearLayout parent,String text,String role){
        if(text.trim().isEmpty()) return;

        TextView body=new TextView(this);
        body.setText(text.trim());
        body.setTextColor(Color.WHITE);
        body.setTextSize(18);
        body.setTextIsSelectable(true);
        body.setLongClickable(true);
        body.setPadding(18,14,18,14);
        body.setBackgroundResource(
            role.equals("user")?R.drawable.bg_message_user:R.drawable.bg_message_ai
        );

        LinearLayout.LayoutParams bp=
            new LinearLayout.LayoutParams(-1,-2);
        bp.setMargins(0,6,0,6);
        parent.addView(body,bp);
    }

    void addCodeBlock(LinearLayout parent,String code){
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(14,10,14,12);
        card.setBackgroundColor(Color.rgb(24,25,32));

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView label=new TextView(this);
        label.setText("CODE");
        label.setTextColor(purple);
        label.setTextSize(13);
        label.setTypeface(null,1);

        Button copy=new Button(this);
        copy.setText("Copy");
        copy.setOnClickListener(v->{
            ClipboardManager cm=
                (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(cm!=null){
                cm.setPrimaryClip(
                    ClipData.newPlainText("MyAssistant code",code)
                );
                Toast.makeText(this,"Code copied ✓",Toast.LENGTH_SHORT).show();
            }
        });

        header.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(copy,new LinearLayout.LayoutParams(-2,-2));

        TextView codeView=new TextView(this);
        codeView.setText(code);
        codeView.setTextColor(Color.rgb(235,235,240));
        codeView.setTextSize(15);
        codeView.setTypeface(android.graphics.Typeface.MONOSPACE);
        codeView.setTextIsSelectable(true);
        codeView.setLongClickable(true);
        codeView.setPadding(4,12,4,8);

        card.addView(header);
        card.addView(codeView,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout.LayoutParams cp=
            new LinearLayout.LayoutParams(-1,-2);
        cp.setMargins(0,8,0,8);
        parent.addView(card,cp);
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
        String key=BuildConfig.GROQ_API_KEY;
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

    void toggleSidebar(){
        if(sidebar.getVisibility()==View.VISIBLE) closeSidebar();
        else {
            renderSidebar();
            sidebar.setVisibility(View.VISIBLE);
            findViewById(R.id.sidebarScrim).setVisibility(View.VISIBLE);
        }
    }

    void closeSidebar(){
        sidebar.setVisibility(View.GONE);
        findViewById(R.id.sidebarScrim).setVisibility(View.GONE);
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
