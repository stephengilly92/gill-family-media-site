package com.apkvault.app;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.view.*;
import android.widget.*;
import android.text.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
public class MainActivity extends Activity {
 static final int BLUE=0xff247bff, DARK=0xff10213c;
 final ArrayList<ApplicationInfo> apps=new ArrayList<>(), shown=new ArrayList<>();
 LinearLayout root, rows; EditText search; TextView count; int mode=0; ApplicationInfo pending;
 final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
 int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
 TextView text(String v,int size){TextView t=new TextView(this);t.setText(v);t.setTextSize(size);t.setTextColor(DARK);return t;}
 public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(0xfff6f8fc);getWindow().setNavigationBarColor(0xfff6f8fc);
  root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(16),dp(16),dp(16),dp(8));root.setBackgroundColor(0xfff6f8fc);
  TextView heading=text("APK Vault",28);heading.setTypeface(null,Typeface.BOLD);root.addView(heading);
  TextView hint=text("Export your original installed APKs",13);root.addView(hint);
  search=new EditText(this);search.setSingleLine(true);search.setHint("Search apps");root.addView(search);
  search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int before,int count){update();}public void afterTextChanged(Editable e){}});
  LinearLayout buttons=new LinearLayout(this);root.addView(buttons);String[] labels={"All","User","System","Export visible"};
  for(int i=0;i<4;i++){final int k=i;Button btt=new Button(this);btt.setText(labels[i]);btt.setTextSize(11);buttons.addView(btt,new LinearLayout.LayoutParams(0,dp(50),1));btt.setOnClickListener(v->{if(k==3)exportAll();else{mode=k;update();}});}
  count=text("Loading apps...",13);root.addView(count);
  ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));rows=new LinearLayout(this);rows.setOrientation(1);scroll.addView(rows);
  setContentView(root);worker.execute(()->{try{List<ApplicationInfo> list=getPackageManager().getInstalledApplications(0);apps.addAll(list);Collections.sort(apps,(a,c)->label(a).compareToIgnoreCase(label(c)));}catch(Exception e){}runOnUiThread(this::update);});
 }
 String label(ApplicationInfo a){try{return getPackageManager().getApplicationLabel(a).toString();}catch(Exception e){return a.packageName;}}
 boolean isSystem(ApplicationInfo a){return (a.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;}
 void update(){if(rows==null)return;shown.clear();rows.removeAllViews();String q=search.getText().toString().toLowerCase(Locale.ROOT);
  for(ApplicationInfo a:apps){if(mode==1&&isSystem(a)||mode==2&&!isSystem(a))continue;String name=label(a);if(!name.toLowerCase(Locale.ROOT).contains(q)&&!a.packageName.toLowerCase(Locale.ROOT).contains(q))continue;shown.add(a);
   LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(dp(3),dp(10),dp(3),dp(10));rows.addView(line);
   ImageView icon=new ImageView(this);try{icon.setImageDrawable(getPackageManager().getApplicationIcon(a));}catch(Exception e){icon.setImageResource(android.R.drawable.sym_def_app_icon);}line.addView(icon,new LinearLayout.LayoutParams(dp(45),dp(45)));
   LinearLayout desc=new LinearLayout(this);desc.setOrientation(1);desc.setPadding(dp(12),0,0,0);line.addView(desc,new LinearLayout.LayoutParams(0,-2,1));
   TextView n=text(name,15);n.setTypeface(null,Typeface.BOLD);n.setSingleLine(true);n.setEllipsize(TextUtils.TruncateAt.END);desc.addView(n);
   TextView sub=text(a.packageName,11);sub.setSingleLine(true);sub.setEllipsize(TextUtils.TruncateAt.MIDDLE);desc.addView(sub);
   Button save=new Button(this);save.setText(a.splitSourceDirs==null?"APK":"ZIP");save.setTextColor(BLUE);line.addView(save);save.setOnClickListener(v->choose(a));
  }count.setText(shown.size()+" apps · "+apps.size()+" installed");
 }
 void choose(ApplicationInfo a){pending=a;boolean zip=a.splitSourceDirs!=null&&a.splitSourceDirs.length>0;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(zip?"application/zip":"application/vnd.android.package-archive");intent.putExtra(Intent.EXTRA_TITLE,a.packageName+(zip?".zip":".apk"));startActivityForResult(intent,10);}
 void exportAll(){if(shown.isEmpty())return;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);startActivityForResult(i,11);}
 @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
  if(req==10){ApplicationInfo a=pending;if(a==null)return;worker.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri)){copyApp(a,out);notice("Exported "+label(a));}catch(Exception e){notice("Export failed: "+e.getMessage());}});}
  if(req==11){ArrayList<ApplicationInfo> selected=new ArrayList<>(shown);worker.execute(()->{int ok=0;for(ApplicationInfo a:selected){try{boolean zip=a.splitSourceDirs!=null&&a.splitSourceDirs.length>0;Uri folder=DocumentsContract.buildDocumentUriUsingTree(uri,DocumentsContract.getTreeDocumentId(uri));Uri dest=DocumentsContract.createDocument(getContentResolver(),folder,zip?"application/zip":"application/vnd.android.package-archive",a.packageName+(zip?".zip":".apk"));try(OutputStream out=getContentResolver().openOutputStream(dest)){copyApp(a,out);}ok++;}catch(Exception ignored){}}notice("Exported "+ok+" of "+selected.size()+" apps");});}
 }
 void copyApp(ApplicationInfo a,OutputStream out)throws IOException{if(a.splitSourceDirs==null||a.splitSourceDirs.length==0){copy(a.sourceDir,out);return;}ZipOutputStream zip=new ZipOutputStream(out);entry(zip,a.sourceDir,"base.apk");int i=0;for(String path:a.splitSourceDirs){String name=new File(path).getName();if(name.equals("base.apk"))name="split_"+i+".apk";entry(zip,path,name);i++;}zip.finish();}
 void entry(ZipOutputStream zip,String path,String name)throws IOException{zip.putNextEntry(new ZipEntry(name));copy(path,zip);zip.closeEntry();}
 void copy(String path,OutputStream out)throws IOException{try(InputStream in=new FileInputStream(path)){byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}}
 void notice(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_LONG).show());}
 @Override protected void onDestroy(){worker.shutdown();super.onDestroy();}
}
