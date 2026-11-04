package com.exemplo.exploradorzip;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

public class MainActivity extends Activity {
  static final int FOLDER=1, ZIP=2, EXTRACT=3;
  final ExecutorService worker=Executors.newSingleThreadExecutor();
  final ArrayList<Row> rows=new ArrayList<>();
  final ArrayList<String> pathNames=new ArrayList<>(), pathIds=new ArrayList<>();
  final ArrayList<ZItem> zipItems=new ArrayList<>();
  Uri root, zipFile; String docId,zipName="",prefix="";
  boolean inZip=false, hidden=false, busy=false;
  TextView path,status; ListView list; Button extract;

  static class Row {
    String name,id,note; boolean folder,zip;
    Row(String n,String i,String s,boolean f,boolean z){name=n;id=i;note=s;folder=f;zip=z;}
    public String toString(){return (folder?"📁 ":zip?"🗜 ":"📄 ")+name+"\n"+note;}
  }
  static class ZItem {
    String name; boolean folder; long size;
    ZItem(String n,boolean f,long s){name=n;folder=f;size=s;}
  }
  @Override public void onCreate(Bundle b){super.onCreate(b);
    LinearLayout base=new LinearLayout(this);base.setOrientation(1);base.setPadding(14,12,14,12);base.setBackgroundColor(0xfff6f8fb);
    TextView heading=new TextView(this);heading.setText("Explorador ZIP  ·  Offline");heading.setTextSize(22);heading.setTextColor(0xff172b48);base.addView(heading);
    LinearLayout bar=new LinearLayout(this);bar.setOrientation(0);
    Button folder=button("📁 Pasta"),z=button("🗜 ZIP"),back=button("← Voltar");
    bar.addView(folder,new LinearLayout.LayoutParams(0,-2,1));bar.addView(z,new LinearLayout.LayoutParams(0,-2,1));bar.addView(back,new LinearLayout.LayoutParams(0,-2,1));base.addView(bar);
    folder.setOnClickListener(v->chooseFolder(FOLDER));z.setOnClickListener(v->chooseZip());back.setOnClickListener(v->goBack());
    CheckBox check=new CheckBox(this);check.setText("Mostrar ocultos (.github, .gitignore...)");
    check.setOnCheckedChangeListener((v,on)->{hidden=on;refresh();});base.addView(check);
    path=new TextView(this);path.setTextColor(0xff275786);path.setPadding(2,8,2,8);base.addView(path);
    list=new ListView(this);base.addView(list,new LinearLayout.LayoutParams(-1,0,1));
    list.setOnItemClickListener((a,v,pos,id)->openRow(pos));
    extract=button("Extrair ZIP em nova pasta...");base.addView(extract);
    extract.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Extrair ZIP")
      .setMessage("O ZIP original será preservado. A extração cria uma nova subpasta no destino selecionado. Arquivos extraídos podem receber novas datas.")
      .setNegativeButton("Cancelar",null).setPositiveButton("Selecionar destino",(d,w)->chooseFolder(EXTRACT)).show());
    status=new TextView(this);status.setTextColor(Color.DKGRAY);status.setPadding(0,9,0,0);base.addView(status);
    setContentView(base);render("Escolha uma pasta ou um ZIP.");
  }
  Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(12);b.setAllCaps(false);return b;}
  void chooseFolder(int request){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,request);}
  void chooseZip(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,ZIP);}
  @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
    if(result!=RESULT_OK||data==null||data.getData()==null)return;
    Uri u=data.getData();try{
      if(request==FOLDER){getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);root=u;docId=DocumentsContract.getTreeDocumentId(u);pathNames.clear();pathIds.clear();inZip=false;zipFile=null;refresh();}
      if(request==ZIP){getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);loadZip(u,queryName(u));}
      if(request==EXTRACT)extractZip(u);
    }catch(Exception e){fail(e.getMessage());}
  }
  String queryName(Uri uri){try(Cursor c=getContentResolver().query(uri,new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return "arquivo.zip";}
  void render(String s){path.setText(inZip?"ZIP: "+zipName+" / "+prefix:root==null?"Nenhuma pasta selecionada":"Pasta / "+String.join(" / ",pathNames));
    extract.setVisibility(inZip?View.VISIBLE:View.GONE);status.setText(s);
    ArrayList<String> labels=new ArrayList<>();for(Row r:rows)labels.add(r.toString());list.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,labels));
  }
  void refresh(){if(inZip){showZip();return;}if(root==null||docId==null){rows.clear();render("Abra uma pasta ou ZIP.");return;}if(busy)return;
    busy=true;status.setText("Lendo pasta...");Uri snapshot=root;String id=docId;
    worker.execute(()->{ArrayList<Row> out=new ArrayList<>();String err=null;
      try{Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(snapshot,id);
        String[] projection={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE};
        try(Cursor c=getContentResolver().query(children,projection,null,null,null)){
          if(c==null)throw new IOException("Pasta indisponível");
          while(c.moveToNext()){String entryId=c.getString(0),name=c.getString(1),mime=c.getString(2);long size=c.isNull(3)?-1:c.getLong(3);
            if(name==null||(!hidden&&name.startsWith(".")))continue;
            boolean isDir=DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
            boolean isZip=!isDir&&name.toLowerCase(Locale.ROOT).endsWith(".zip");
            out.add(new Row(name,entryId,isDir?"Pasta":isZip?"Toque para abrir sem extrair":sizeString(size),isDir,isZip));
          }
        }
        sort(out);
      }catch(Exception e){err=e.getMessage();}
      String msg=err;runOnUiThread(()->{busy=false;if(inZip||!snapshot.equals(root)||!id.equals(docId))return;rows.clear();rows.addAll(out);render(msg==null?out.size()+" itens":"Erro: "+msg);});
    });
  }
  static void sort(ArrayList<Row> a){a.sort(Comparator.comparing((Row r)->!r.folder).thenComparing(r->r.name.toLowerCase(Locale.ROOT)));}
  void openRow(int pos){if(busy||pos<0||pos>=rows.size())return;Row r=rows.get(pos);
    if(inZip){if(r.folder){prefix=r.id;showZip();}else new AlertDialog.Builder(this).setTitle(r.name).setMessage("Arquivo dentro do ZIP.\n"+r.note+"\n\nNenhuma extração foi feita.").setPositiveButton("OK",null).show();}
    else if(r.folder){pathNames.add(r.name);pathIds.add(r.id);docId=r.id;refresh();}
    else if(r.zip)loadZip(DocumentsContract.buildDocumentUriUsingTree(root,r.id),r.name);
    else new AlertDialog.Builder(this).setTitle(r.name).setMessage("Arquivo comum: "+r.note+"\nEsta versão foi criada para navegar por ZIPs.").setPositiveButton("OK",null).show();
  }
  void loadZip(Uri uri,String name){if(busy)return;busy=true;status.setText("Lendo ZIP sem extrair...");
    worker.execute(()->{ArrayList<ZItem> out=new ArrayList<>();String err=null;
      try(InputStream input=getContentResolver().openInputStream(uri)){
        if(input==null)throw new IOException("Acesso ao arquivo negado");
        try(ZipInputStream zip=new ZipInputStream(input)){
          ZipEntry e;int count=0;while((e=zip.getNextEntry())!=null){if(++count>12000)throw new IOException("Mais de 12 mil entradas no ZIP");String entry=e.getName().replace('\\','/');
            while(entry.startsWith("./"))entry=entry.substring(2);
            if(!entry.isEmpty())out.add(new ZItem(entry,e.isDirectory(),e.getSize()));zip.closeEntry();
          }
        }
      }catch(Exception e){err=e.getMessage();}
      String msg=err;runOnUiThread(()->{busy=false;if(msg!=null){fail("Não foi possível ler ZIP: "+msg);return;}zipFile=uri;zipName=name;zipItems.clear();zipItems.addAll(out);prefix="";inZip=true;showZip();});
    });
  }
  void showZip(){if(!inZip)return;Map<String,Row> opts=new HashMap<>();
    for(ZItem z:zipItems){if(!z.name.startsWith(prefix)||z.name.equals(prefix))continue;
      String rest=z.name.substring(prefix.length());int slash=rest.indexOf('/');
      String seg=slash<0?rest:rest.substring(0,slash);
      if(seg.isEmpty()||(!hidden&&seg.startsWith(".")))continue;
      boolean folder=slash>=0||z.folder;
      opts.put(seg,new Row(seg,folder?prefix+seg+"/":z.name,folder?"Pasta interna":sizeString(z.size),folder,false));
    }
    rows.clear();rows.addAll(opts.values());sort(rows);render(rows.size()+" itens • "+zipItems.size()+" entradas no ZIP");
  }
  void goBack(){if(busy)return;
    if(inZip){if(!prefix.isEmpty()){String p=prefix.substring(0,prefix.length()-1);int i=p.lastIndexOf('/');prefix=i<0?"":p.substring(0,i+1);showZip();}
      else {inZip=false;zipFile=null;zipItems.clear();refresh();}}
    else if(!pathNames.isEmpty()){pathNames.remove(pathNames.size()-1);pathIds.remove(pathIds.size()-1);docId=pathIds.isEmpty()?DocumentsContract.getTreeDocumentId(root):pathIds.get(pathIds.size()-1);refresh();}
    else Toast.makeText(this,"Você está na pasta inicial",Toast.LENGTH_SHORT).show();
  }
  static String sizeString(long n){if(n<0)return "Tamanho desconhecido";if(n<1024)return n+" B";return String.format(Locale.ROOT,n<1048576?"%.1f KB":"%.1f MB",n<(1048576)?n/1024.0:n/1048576.0);}
  void fail(String s){status.setText(s);Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
  @Override public void onBackPressed(){goBack();}
  @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
  static boolean safe(String name){
    if(name.startsWith("/")||name.startsWith("\\")||name.contains(":")||name.indexOf('\0')>=0)return false;
    for(String segment:name.split("/"))if(segment.equals(".."))return false;
    return true;
  }
  void extractZip(Uri destination){if(busy||zipFile==null)return;
    Uri source=zipFile;String title=zipName;busy=true;status.setText("Extraindo... Não feche o aplicativo.");
    worker.execute(()->{
      String err=null;int files=0;long bytes=0;
      try{
        android.content.ContentResolver resolver=getContentResolver();
        Uri rootDoc=DocumentsContract.buildDocumentUriUsingTree(destination,DocumentsContract.getTreeDocumentId(destination));
        String base=title.replaceAll("(?i)\\.zip$","").replaceAll("[^A-Za-z0-9._ -]","_");
        if(base.isEmpty()||base.equals(".")||base.equals(".."))base="arquivo";
        Uri output=DocumentsContract.createDocument(resolver,rootDoc,DocumentsContract.Document.MIME_TYPE_DIR,base+"_extraido_"+System.currentTimeMillis());
        if(output==null)throw new IOException("Não foi possível criar a pasta de destino");
        Map<String,Uri> made=new HashMap<>();made.put("",output);
        try(InputStream in=resolver.openInputStream(source)){
          if(in==null)throw new IOException("Não foi possível ler ZIP");
          try(ZipInputStream zip=new ZipInputStream(in)){
            byte[] buffer=new byte[32768];ZipEntry e;int entryCount=0;
            while((e=zip.getNextEntry())!=null){
              if(++entryCount>12000)throw new IOException("Limite de itens excedido");
              String name=e.getName().replace('\\','/');while(name.startsWith("./"))name=name.substring(2);
              if(name.isEmpty()){zip.closeEntry();continue;}
              if(!safe(name))throw new IOException("Caminho inseguro: "+name);
              String[] pieces=name.split("/");String parent="";
              int subCount=e.isDirectory()?pieces.length:pieces.length-1;
              for(int k=0;k<subCount;k++){
                if(pieces[k].isEmpty())continue;
                String child=parent+pieces[k]+"/";
                if(!made.containsKey(child)){
                  Uri newDir=DocumentsContract.createDocument(resolver,made.get(parent),DocumentsContract.Document.MIME_TYPE_DIR,pieces[k]);
                  if(newDir==null)throw new IOException("Erro criando pasta: "+child);
                  made.put(child,newDir);
                }
                parent=child;
              }
              if(!e.isDirectory()){
                String fileName=pieces[pieces.length-1];
                if(fileName.isEmpty())throw new IOException("Nome vazio");
                Uri file=DocumentsContract.createDocument(resolver,made.get(parent),"application/octet-stream",fileName);
                if(file==null)throw new IOException("Erro criando arquivo: "+name);
                try(OutputStream out=resolver.openOutputStream(file,"w")){
                  if(out==null)throw new IOException("Falha ao abrir arquivo de saída");
                  long fileBytes=0;int length;
                  while((length=zip.read(buffer))!=-1){
                    fileBytes+=length;bytes+=length;
                    if(fileBytes>150L*1024*1024||bytes>500L*1024*1024)throw new IOException("Limite de extração (150 MB por arquivo, 500 MB no total)");
                    out.write(buffer,0,length);
                  }
                }
                files++;
              }
              zip.closeEntry();
            }
          }
        }
      }catch(Exception e){err=e.getMessage();}
      final int count=files;final long total=bytes;final String message=err;
      runOnUiThread(()->{busy=false;
        if(message==null){render("Extraídos "+count+" arquivos ("+sizeString(total)+")");
          new AlertDialog.Builder(this).setTitle("Extração concluída").setMessage("Extraídos "+count+" arquivos numa nova subpasta. ZIP original preservado. Datas de arquivos extraídos podem mudar.").setPositiveButton("OK",null).show();}
        else{render("Falha: "+message);
          new AlertDialog.Builder(this).setTitle("Extração incompleta").setMessage(message+"\n\nUma pasta parcialmente extraída pode permanecer no destino.").setPositiveButton("OK",null).show();}
      });
    });
  }
}
