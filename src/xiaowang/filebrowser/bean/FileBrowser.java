package xiaowang.filebrowser.bean;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import android.os.Environment;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import xiaowang.filebrowser.adapter.FileSimpleAdapter;
import xiaowang.filebrowser.biz.FileHelper;
import xiaowang.filebrowser.biz.FileMD5;
import xiaowang.filebrowser.biz.FileOperator;
import xiaowang.filebrowser.biz.MIMEType;
import xiaowang.filebrowser.widget.MarqueeView;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AbsListView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.Toast;

/*
 * 文件列表视图（默认SD卡）
 * 按下表单项 根据是否为目录 判断 
 * 1.若是文件，则跳转
 * 2.若是文件夹，则重绘目标文件夹的文件列表
 * 注意：要修改文件需要权限，在AndroidManifest.xml中添加
 * <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"></uses-permission>
 */

public class FileBrowser extends ListActivity {
    private boolean firstSort = true;// 是否先排文件夹后排文件
    private List<File> fileList;
    private ListView listv;
    private File file, fileNow, fileParent;
    private int filePosition = 0;
    private boolean isFlag;// 是否按下返回键的标识
    private FileSimpleAdapter adapter;
    private int fsd;// 文件夹排序方式
    private int fsf;// 文件排序方式
    private SharedPreferences.Editor editor;
    private SharedPreferences sp;
    private List<Integer> fileSelecteds;
    private List<File> moves = new ArrayList<File>();
    private List<File> copys = new ArrayList<File>();
    private MarqueeView marqueeView;
    private ProgressBar progressBar;
    private Dialog menuDialog;

    public static HashMap<String, String> fileSignature = new HashMap<String, String>();
    private final String DATABASE_PATH = android.os.Environment
            .getExternalStorageDirectory().getAbsolutePath() + "/dictionary";
    private final String DATABASE_FILENAME = "dictionary.db2";
    SQLiteDatabase database;
    public static ArrayList<String> tempPath = new ArrayList<String>();
    PackageManager pm;
    public static  List<ApplicationInfo> badApp_infos;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // requestWindowFeature(Window.FEATURE_NO_TITLE);//注意顺序
        // getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
        // WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.listview);// 注意顺序

        database = openDatabase();
        pm = getPackageManager();

        listv = getListView();
        sp = getSharedPreferences("setting", Activity.MODE_PRIVATE);
        editor = sp.edit();
        if (sp.contains("dir_sort")) {
            fsd = sp.getInt("dir_sort", -1);
            fsf = sp.getInt("file_sort", 0);
            firstSort = sp.getBoolean("first_sort", true);
        } else {
            fsd = FileOperator.NAME_ASC;
            fsf = FileOperator.NAME_ASC;
        }

        registerForContextMenu(listv);

        progressBar = (ProgressBar) findViewById(R.id.title_progressbar);
        marqueeView = (MarqueeView) findViewById(R.id.marquee_text);
        marqueeView.setText("/sdcard");

        if (Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED)) {
            File path = Environment.getExternalStorageDirectory();
            fileList(path);

        }

        listv.setOnScrollListener(new OnScrollListener() {

            public void onScrollStateChanged(AbsListView view, int scrollState) {
                switch (scrollState) {
                case SCROLL_STATE_FLING:
                case SCROLL_STATE_TOUCH_SCROLL:
                    // adapter.setLoad(false);
                    break;
                case SCROLL_STATE_IDLE:
                    // adapter.setLoad(true);
                    // adapter.notifyDataSetChanged();
                    break;
                }
            }

            public void onScroll(AbsListView view, int firstVisibleItem,
                    int visibleItemCount, int totalItemCount) {

            }
        });
        listv.setOnItemLongClickListener(new OnItemLongClickListener() {

            public boolean onItemLongClick(AdapterView<?> parent, View view,
                    int position, long id) {
                fileNow = fileList.get(position);
                filePosition = position;
                removeDialog(1);
                removeDialog(2);
                TableLayout tableLayout = (TableLayout) getLayoutInflater()
                        .inflate(R.layout.menu_item, null);
                menuDialog = new AlertDialog.Builder(FileBrowser.this).setView(
                        tableLayout).create();
                menuDialog.show();
                menuDialog.setCanceledOnTouchOutside(true);
                return true;
            }
        });
    }

    // 有registerReceiver（）注册广播，就有unregisterReceiver（）方法，他们是成对出现的。
    // 如果在onCreate（）方法中注册广播，就在onDestroy（）方法中释放。
    // 如果在onResume（）方法中注册广播，就在onPause（）方法中释放。
    private final BroadcastReceiver sdcardListener = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            if (Intent.ACTION_MEDIA_MOUNTED.equals(action)
                    || Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action)) {
                File path = Environment.getExternalStorageDirectory();
                fileList(path);
            }
        }
    };

    private void registerSDCardListener() {
        IntentFilter intentFilter = new IntentFilter(
                Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        intentFilter.addDataScheme("file");
        registerReceiver(sdcardListener, intentFilter);
    }

    protected void onResume() {

        super.onResume();
        registerSDCardListener();

    }

    protected void onPause() {

        super.onPause();
        unregisterReceiver(sdcardListener);
    }

    // 读取文件列表,并设置listView
    public void fileList(final File path) {
        progressBar.setVisibility(View.VISIBLE);
        new AsyncTask<String, String, String>() {

            protected String doInBackground(String... params) {
                ArrayList<HashMap<String, Object>> data = FileOperator
                        .fileList(FileBrowser.this, path, fsd, fsf, firstSort);
                fileList = FileOperator.fileList;
                adapter = new FileSimpleAdapter(FileBrowser.this, data,
                        R.layout.listfiles, new String[] { "fileIcon",
                                "fileName", "fileInfo", "checkBox" },
                        new int[] { R.id.fileIcon, R.id.fileName,
                                R.id.fileInfo, R.id.fileCheckBox });
                return null;
            }

            protected void onPostExecute(String result) {
                progressBar.setVisibility(View.GONE);
                // 设置ListActivity界面的方法
                // 同setContentView在Activity界面中的作用
                setListAdapter(adapter);
                // 列表更新动画
                Animation anim = AnimationUtils.loadAnimation(FileBrowser.this,
                        R.anim.zoomin);
                listv.startAnimation(anim);
                if (isFlag)
                    listv.setSelection(filePosition);
                super.onPostExecute(result);
            }
        }.execute();

    }

    // 列表项被点击的动作
    // 1.如果是文件夹 则重置绘图 进入文件夹
    // 2.如果是文件 则带数据 跳转屏幕(Activity)
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {

        // position为点击所在的位置 用这个序号来取得对应文件
        file = fileList.get(position);
        filePosition = position;

        if (file.isDirectory() && file.canRead()) {

            File[] f = file.listFiles();
            if (f.length > 0) {
                fileParent = file;
                marqueeView.setText(file.getAbsolutePath().substring(4));
                isFlag = false;
                fileList(file);
            } else {
                fileParent = file.getParentFile();
                Toast.makeText(this, "文件夹为空", Toast.LENGTH_LONG).show();
            }

        } else {
            MIMEType.openFile(file, this);
        }
        overridePendingTransition(R.anim.slide_left_in, R.anim.slide_left_out);
    }

    File moveFile;
    FileInputStream fis = null;
    String str;

    public void onClick(View item) {

        switch (item.getId()) {
        case R.id.btn_move:
            fileSelecteds = adapter.getSelecteds();
            if (fileSelecteds != null) {
                for (Integer i : fileSelecteds) {
                    moveFile = fileList.get(i);
                    moves.add(moveFile);
                }
            }

            break;
        case R.id.btn_moveto:
            if (!moves.isEmpty()) {
                for (File move : moves) {
                    FileOperator.move(move, fileNow);
                }
                Toast.makeText(FileBrowser.this, "移动完成", Toast.LENGTH_LONG)
                        .show();

                adapter.noneIsSelected();
                moves.removeAll(moves);
                fileSelecteds = null;
                isFlag = true;
                fileList(fileNow.getParentFile());
            } else {
                Toast.makeText(FileBrowser.this, "没有文件被移动，请勾选文件并移动",
                        Toast.LENGTH_LONG).show();
            }

            break;
        case R.id.btn_delete:
            fileSelecteds = adapter.getSelecteds();

            showDialog(1);

            break;
        case R.id.btn_rename:

            showDialog(2);
            break;
        case R.id.btn_compress:

            new AsyncTask<Object, Integer, Void>() {

                protected void onPreExecute() {
                    super.onPreExecute();
                    showDialog(4);
                }

                protected Void doInBackground(Object... params) {

                    try {
                        FileOperator.CreateZipFile(fileNow.getAbsolutePath(),
                                fileNow.getAbsolutePath() + ".zip");
                    } catch (Exception e) {

                        e.printStackTrace();
                    }

                    return null;
                }

                protected void onProgressUpdate(Integer... values) {
                    super.onProgressUpdate(values);

                }

                protected void onPostExecute(Void result) {
                    dismissDialog(4);
                    Toast.makeText(FileBrowser.this, "压缩完成", Toast.LENGTH_LONG)
                            .show();
                    fileList(fileNow.getParentFile());
                    listv.setSelection(filePosition);
                    super.onPostExecute(result);
                }

            }.execute();
            break;

        case R.id.btn_uncompress:
            new AsyncTask<String, Integer, Void>() {

                protected void onPreExecute() {
                    super.onPreExecute();
                    showDialog(4);
                }

                protected Void doInBackground(String... params) {

                    try {
                        FileOperator.deCompress(fileNow.getAbsolutePath(),
                                fileNow.getParent());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return null;
                }

                protected void onProgressUpdate(Integer... values) {

                    super.onProgressUpdate(values);
                }

                protected void onPostExecute(Void result) {
                    dismissDialog(4);
                    Toast.makeText(FileBrowser.this, "解压缩完成", Toast.LENGTH_LONG)
                            .show();
                    fileList(fileNow.getParentFile());
                    super.onPostExecute(result);
                }

            }.execute();

            break;
        case R.id.btn_copy:
            fileSelecteds = adapter.getSelecteds();
            if (fileSelecteds != null) {
                for (Integer i : fileSelecteds) {
                    moveFile = fileList.get(i);
                    copys.add(moveFile);
                }
            }
            break;
        case R.id.btn_post:
            if (new File("system/xbin/cp").exists()) {
                new AsyncTask<Object, Integer, String>() {

                    protected void onPreExecute() {
                        showDialog(4);
                    }

                    protected String doInBackground(Object... params) {
                        if (copys.isEmpty())
                            return "failed";

                        for (File copy : copys) {
                            FileOperator.copy(copy, fileNow);
                        }
                        return "success";
                    }

                    protected void onPostExecute(String result) {
                        removeDialog(4);
                        if (result == "success") {
                            Toast.makeText(FileBrowser.this, "文件已粘贴", 2000)
                                    .show();
                            if (fileNow.isFile()) {
                                fileList(fileNow.getParentFile());
                            }
                            adapter.noneIsSelected();
                            copys.removeAll(copys);
                            fileSelecteds = null;
                        } else {
                            Toast.makeText(FileBrowser.this,
                                    "未有文件被复制，请勾选文件并复制", 2000).show();
                        }
                        super.onPostExecute(result);
                    }
                }.execute();

            } else {
                new AsyncTask<Object, Integer, String>() {

                    protected void onPreExecute() {

                        super.onPreExecute();
                        showDialog(4);
                    }

                    protected String doInBackground(Object... params) {
                        if (copys.isEmpty())
                            return "failed";

                        for (File copy : copys) {
                            if (fileNow.isDirectory()) {
                                FileOperator.copy3(copy, fileNow);
                            } else {
                                FileOperator.copy3(copy,
                                        fileNow.getParentFile());
                            }
                        }
                        return "success";
                    }

                    @SuppressLint("NewApi")
                    protected void onPostExecute(String result) {

                        super.onPostExecute(result);
                        dismissDialog(4);
                        if (result == "success") {
                            Toast.makeText(FileBrowser.this, "粘贴已完成",
                                    Toast.LENGTH_LONG).show();
                            fileList(fileNow.getParentFile());
                            listv.setSelection(filePosition);
                            adapter.noneIsSelected();
                            copys.removeAll(copys);
                            fileSelecteds = null;
                        } else {
                            Toast.makeText(FileBrowser.this,
                                    "未有文件被复制，请勾选文件并复制", Toast.LENGTH_LONG)
                                    .show();
                        }
                    }
                }.execute();
            }

            break;
        case R.id.btn_newcreate:
            showDialog(0);
            break;
        default:
            break;
        }
        menuDialog.dismiss();
    }

    // 重写按下返回键触发的事件
    public void onBackPressed() {

        if (file == null) {
            super.onBackPressed();
        } else if (!fileParent.getName().equalsIgnoreCase("sdcard")) {
            isFlag = true;
            fileList(fileParent.getParentFile());

            fileParent = fileParent.getParentFile();
            marqueeView.setText(fileParent.getAbsolutePath().substring(4));

        } else {
            finish();
        }
    }

    private ProgressDialog pd;
    private LinearLayout rename = null;
    private EditText etRename = null;

    protected Dialog onCreateDialog(int id) {
        rename = (LinearLayout) getLayoutInflater().inflate(R.layout.rename,
                null);
        etRename = (EditText) rename.findViewById(R.id.etRename);
        etRename.setText(fileNow.getName());
        LinearLayout mkDir = (LinearLayout) getLayoutInflater().inflate(
                R.layout.mkdir, null);
        final EditText etMkDir = (EditText) mkDir.findViewById(R.id.etMkDir);

        switch (id) {
        case 0:
            return new AlertDialog.Builder(this).setTitle("创建新文件夹")
                    .setView(mkDir)
                    .setPositiveButton("确定", new OnClickListener() {

                        public void onClick(DialogInterface dialog, int which) {
                            FileOperator.mkDir(fileNow, etMkDir.getText()
                                    .toString(), FileBrowser.this);
                        }
                    }).setNegativeButton("取消", new OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    }).create();

        case 1:

            return new AlertDialog.Builder(this)
                    .setTitle("是否删除文件？")
                    .setPositiveButton("确定",
                            new DialogInterface.OnClickListener() {

                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    if (!fileSelecteds.isEmpty()) {
                                        for (Integer i : fileSelecteds) {
                                            moveFile = fileList.get(i);
                                            FileOperator.delete(moveFile,
                                                    FileBrowser.this);
                                        }
                                        File[] f = moveFile.getParentFile()
                                                .listFiles();
                                        isFlag = true;
                                        if (f.length != 0) {
                                            fileList(moveFile.getParentFile());
                                        } else {
                                            marqueeView.setText(moveFile
                                                    .getParentFile()
                                                    .getParent().substring(4));
                                            fileList(moveFile.getParentFile()
                                                    .getParentFile());
                                        }
                                        if (fileList.size() > (Collections
                                                .min(fileSelecteds) + 1)
                                                && Collections
                                                        .min(fileSelecteds) - 1 >= 0) {
                                            listv.setSelection(Collections
                                                    .min(fileSelecteds) - 1);
                                        } else {
                                            listv.setSelection(fileList.size() - 1);
                                        }
                                        adapter.noneIsSelected();
                                        fileSelecteds = null;
                                    } else {
                                        Toast.makeText(FileBrowser.this,
                                                "没有文件被删除，请勾选文件",
                                                Toast.LENGTH_LONG).show();
                                    }
                                }
                            })
                    .setNegativeButton("取消",
                            new DialogInterface.OnClickListener() {

                                public void onClick(DialogInterface dialog,
                                        int which) {

                                }
                            }).create();
        case 2:
            return new AlertDialog.Builder(this)
                    .setTitle("文件重命名")
                    .setView(rename)
                    .setPositiveButton("确定",
                            new DialogInterface.OnClickListener() {

                                public void onClick(DialogInterface dialog,
                                        int which) {

                                    fileNow.renameTo(new File(fileNow
                                            .getParentFile(), etRename
                                            .getText().toString()));

                                    fileList(fileNow.getParentFile());
                                    listv.setSelection(filePosition);
                                    Toast.makeText(FileBrowser.this, "重命名已完成",
                                            1000).show();
                                }
                            })
                    .setNegativeButton("取消",
                            new DialogInterface.OnClickListener() {

                                public void onClick(DialogInterface dialog,
                                        int which) {

                                }
                            }).create();
        case 3:
            pd = new ProgressDialog(FileBrowser.this);
            pd.setCancelable(true);
            pd.setMax(100);
            pd.setMessage("正在工作中，请稍候。。。");
            pd.setIndeterminate(false);
            pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            pd.show();
            return pd;
        case 4:
            pd = new ProgressDialog(FileBrowser.this);
            pd.setCancelable(true);
            pd.setMax(100);
            pd.setMessage("正在工作中，请稍候。。。");
            pd.setIndeterminate(false);
            pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            pd.show();
            return pd;
        }
        return null;
    }

    public boolean onCreateOptionsMenu(Menu menu) {

        menu.add(1, 0, 0, "文件排序").setIcon(
                android.R.drawable.ic_menu_sort_alphabetically);
        menu.add(1, 1, 1, "退出").setIcon(android.R.drawable.ic_lock_power_off);
        menu.add(1, 2, 2, "文件搜索").setIcon(android.R.drawable.ic_menu_search);
        menu.add(1, 3, 3, "关于")
                .setIcon(android.R.drawable.ic_menu_info_details);
        menu.add(1, 4, 4, "病毒扫描").setIcon(android.R.drawable.ic_menu_search);
        menu.add(1, 5, 5, "权限扫描").setIcon(android.R.drawable.ic_menu_search);
        menu.add(1, 6, 6, "更新").setIcon(android.R.drawable.ic_menu_search);
        menu.add(1, 7, 7, "程序扫描").setIcon(android.R.drawable.ic_menu_search);
        return super.onCreateOptionsMenu(menu);
    }

    private String[] sorts = { "先排文件夹后排文件", "先排文件后排文件夹", "文件夹按字母升序排列",
            "文件夹按字母降序排列", "文件按字母升序排列", "文件按字母降序排列", "文件按后缀名升序排列", "文件按后缀名降序排列",
            "文件按大小升序排列", "文件按大小降序排列", "文件/文件夹升序混排", "文件/文件夹降序混排" };
    private int index = 0;

    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
        case 0:
            new AlertDialog.Builder(this).setTitle("选择排序方式")
                    .setSingleChoiceItems(sorts, -1, new OnClickListener() {

                        public void onClick(DialogInterface dialog, int which) {
                            index = which;
                        }
                    }).setPositiveButton("确定", new OnClickListener() {

                        public void onClick(DialogInterface dialog, int which) {
                            switch (index) {
                            case 0:
                                firstSort = true;
                                break;
                            case 1:
                                firstSort = false;
                                break;
                            case 2:
                                fsd = FileOperator.NAME_ASC;
                                break;
                            case 3:
                                fsd = FileOperator.NAME_DESC;
                                break;
                            case 4:
                                fsf = FileOperator.NAME_ASC;
                                break;
                            case 5:
                                fsf = FileOperator.NAME_DESC;
                                break;
                            case 6:
                                fsf = FileOperator.SUFFIX_ASC;
                                break;
                            case 7:
                                fsf = FileOperator.SUFFIX_DESC;
                                break;
                            case 8:
                                fsf = FileOperator.SIZE_ASC;
                                break;
                            case 9:
                                fsf = FileOperator.SIZE_DESC;
                                break;
                            case 10:
                                fsf = FileOperator.NAME_ASC;
                                fsd = -1;
                                break;
                            case 11:
                                fsf = FileOperator.NAME_DESC;
                                fsd = -1;
                                break;
                            default:
                                break;
                            }
                            editor.putInt("dir_sort", fsd);
                            editor.putInt("file_sort", fsf);
                            editor.putBoolean("first_sort", firstSort);
                            editor.commit();
                            if (file == null) {
                                fileList(Environment
                                        .getExternalStorageDirectory());
                                marqueeView.setText("/sdcard");
                            } else if (file.isDirectory()) {
                                fileList(file);
                                marqueeView.setText(file.getAbsolutePath()
                                        .substring(4));
                            } else {
                                fileList(file.getParentFile());
                                marqueeView.setText(file.getParent().substring(
                                        4));
                            }
                        }
                    }).create().show();
            break;
        case 1:
            FileHelper fileHelper = new FileHelper(FileBrowser.this);
            ArrayList<String> filePaths = fileHelper.getAllFiles(Environment
                    .getExternalStorageDirectory());
            if (filePaths.size() == 0) {
                Log.d("jack", "filePaths为空");
            }
            for (String path : filePaths) {
                String deleteDirPath = path.substring(0, path.indexOf("/zip"));
                fileHelper.deleteSDFile(deleteDirPath);
            }
            FileHelper.zipList.clear();
            finish();
            break;
        case 2:
            resultList = new ArrayList<File>();
            LinearLayout layout = (LinearLayout) getLayoutInflater().inflate(
                    R.layout.scan, null);
            editText = (EditText) layout.findViewById(R.id.edittext);
            new AlertDialog.Builder(this).setView(layout)
                    .setPositiveButton("确定", new OnClickListener() {

                        public void onClick(DialogInterface dialog, int which) {
                            new AsyncTask<Integer, Integer, String[]>() {

                                private ProgressDialog dialog;

                                // 前台显示
                                protected void onPreExecute() {
                                    // 每次扫描前将前一次的查找结果清空
                                    resultList.clear();
                                    dialog = ProgressDialog.show(
                                            FileBrowser.this, "",
                                            "正在搜索,请稍候....");
                                    super.onPreExecute();
                                }

                                // 后台执行
                                protected String[] doInBackground(
                                        Integer... params) {
                                    if (!android.os.Environment
                                            .getExternalStorageState()
                                            .equals(android.os.Environment.MEDIA_MOUNTED)) {

                                    } else {
                                        if (!editText.getText().toString()
                                                .equals("")) {
                                            GetFiles(new File(marqueeView
                                                    .getText().toString()));
                                        }
                                    }
                                    return null;
                                }

                                // 执行完毕
                                protected void onPostExecute(String[] result) {
                                    dialog.dismiss();
                                    if (editText.getText().toString()
                                            .equals("")) {
                                        Toast.makeText(FileBrowser.this,
                                                "请输入有效信息", Toast.LENGTH_SHORT)
                                                .show();
                                    } else {
                                        Toast.makeText(FileBrowser.this,
                                                "扫描完毕", Toast.LENGTH_LONG)
                                                .show();
                                        Intent in = new Intent();
                                        in.setClass(FileBrowser.this,
                                                ScanResult.class);
                                        startActivity(in);
                                    }
                                    super.onPostExecute(result);
                                }
                            }.execute(0);
                        }
                    }).setNegativeButton("取消", new OnClickListener() {

                        public void onClick(DialogInterface dialog, int which) {

                        }
                    }).create().show();
            break;
        case 3:
            ScrollView sv = (ScrollView) getLayoutInflater().inflate(
                    R.layout.textview, null);
            new AlertDialog.Builder(this).setTitle("欢迎使用AntiVirus").setView(sv)
                    .setPositiveButton("关闭", new OnClickListener() {

                        public void onClick(DialogInterface dialog, int which) {

                        }
                    }).create().show();
            break;
        case 4:
            new AsyncTask<Integer, Integer, String[]>() {

                private ProgressDialog dialog;
                Bundle bundle = new Bundle();

                // 前台显示
                protected void onPreExecute() {
                    dialog = ProgressDialog.show(FileBrowser.this, "",
                            "正在查杀,请稍候....");
                    super.onPreExecute();
                }

                // 后台执行
                protected String[] doInBackground(Integer... params) {
                    String sql = "select name,signature from virus";
                    if (!database.isOpen()) {
                        database = openDatabase();
                    }
                    Cursor cursor = database.rawQuery(sql, null);
                    ArrayList<String> virusName = new ArrayList<String>();
                    ArrayList<String> virusPath = new ArrayList<String>();
                    while (cursor.moveToNext()) {
                        String name = cursor.getString(cursor
                                .getColumnIndex("name"));
                        Log.e("test", name);
                        String signature = cursor.getString(cursor
                                .getColumnIndex("signature"));
                        try {
                            HashMap<String, String> fileSignature = getFileSignature(new File(
                                    "/mnt/" + marqueeView.getText())
                                    .listFiles());//
                            Set<String> keys = fileSignature.keySet();
                            for (String key : keys) {
                                if (fileSignature.get(key).equals(signature)) {
                                    virusName.add(name);
                                    virusPath.add("路径:" + key);
                                }
                            }
                            FileBrowser.fileSignature.clear();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (cursor != null) {
                        cursor.close();
                    }
                    if (database.isOpen()) {
                        database.close();
                    }
                    bundle.putStringArrayList("virusName", virusName);
                    bundle.putStringArrayList("virusPath", virusPath);

                    return null;
                }

                // 执行完毕
                protected void onPostExecute(String[] result) {
                    dialog.dismiss();
                    Toast.makeText(FileBrowser.this, "扫描完毕", Toast.LENGTH_LONG)
                            .show();
                    Intent intent = new Intent();
                    intent.setClass(FileBrowser.this, ScannResultActivity.class);
                    intent.putExtras(bundle);
                    startActivity(intent);
                    super.onPostExecute(result);
                }
            }.execute();

            break;
        case 5:
            Intent intent2 = new Intent();
            intent2.setClass(FileBrowser.this, AppListActivity.class);
            startActivity(intent2);
            break;
        case 6:
            new AsyncTask<Integer, Integer, String[]>() {

                private ProgressDialog dialog;

                // 前台显示
                protected void onPreExecute() {
                    dialog = ProgressDialog.show(FileBrowser.this, "",
                            "正在联网更新,请稍候....");
                    super.onPreExecute();
                }

                // 后台执行
                protected String[] doInBackground(Integer... params) {
                    try {
                        Thread.sleep(4000);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                    return null;
                }

                // 执行完毕
                protected void onPostExecute(String[] result) {
                    dialog.dismiss();
                    Toast toast = Toast.makeText(FileBrowser.this,
                            "当前病毒库是最新的，无需更新", Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                    super.onPostExecute(result);
                }
            }.execute();

            break;
        case 7:
            String sql = "select name,signature from virus";
            if (!database.isOpen()) {
                database = openDatabase();
            }
            Cursor cursor = database.rawQuery(sql, null);
            List<HashMap<String, String>> name_signature_list = new ArrayList<HashMap<String, String>>();
            HashMap<String, String> map;
            while (cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndex("name"));
                String signature = cursor.getString(cursor
                        .getColumnIndex("signature"));
                map = new HashMap<String, String>();
                map.put(name, signature);
                name_signature_list.add(map);
            }
            if (cursor != null) {
                cursor.close();
            }

            if (database.isOpen()) {
                database.close();
            }
            List<ApplicationInfo> infos = new ArrayList<ApplicationInfo>();
            badApp_infos = new ArrayList<ApplicationInfo>();
            infos = pm.getInstalledApplications(0);
            for (ApplicationInfo info : infos) {
                for(HashMap<String, String> name_signature:name_signature_list){
                    if (name_signature.containsKey(info.packageName)) {
                        if (name_signature.get(info.packageName).trim().equals(getAppSignature(info.packageName).trim())) {
                            Log.e("test", info.packageName+"是一个病毒");
                            badApp_infos.add(info);
                        }                       
                    }
                }
            }
            Intent i = new Intent(getApplicationContext(), BadAppActivity.class);
            startActivity(i);
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    /*
     * 获取程序的签名
     */
    public String getAppSignature(String packname) {
        try {
            PackageInfo packinfo = pm.getPackageInfo(packname,
                    PackageManager.GET_SIGNATURES);
            // 获取到所有的权限
            return packinfo.signatures[0].toCharsString();
        } catch (NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static List<File> resultList;// 文件列表
    EditText editText;

    public void GetFiles(File filePath) {

        File[] files = filePath.listFiles();

        if (files != null) {
            int len = files.length;
            for (int i = 0; i < len; i++) {
                if (files[i].isDirectory()) {
                    GetFiles(files[i]);
                } else {
                    if (files[i].getName().contains(editText.getText())) {
                        resultList.add(files[i]);
                    }
                }
            }
        }
    }

    public HashMap<String, String> getFileSignature(File[] files)
            throws IOException {

        for (int i = 0; i < files.length; i++) {
            File currentFile = files[i];
            if (currentFile.isDirectory() && currentFile.listFiles() != null) {
                getFileSignature(currentFile.listFiles());
            } else {
                String fileName = currentFile.getName();
                if (fileName.endsWith(".apk")) {
                    FileMD5 md5 = new FileMD5(currentFile.getPath());
                    if (currentFile.getPath().contains("zip")) {
                        tempPath.add(currentFile.getPath());
                        String key = currentFile.getPath().substring(0,
                                currentFile.getPath().indexOf("/zip"))
                                + ".zip";
                        fileSignature.put(key, md5.getMd5());
                    } else {
                        fileSignature.put(currentFile.getPath(), md5.getMd5());
                    }
                } else if (fileName.endsWith(".zip")) {
                    FileHelper.unZip(currentFile.getPath());
                }
            }
        }
        return fileSignature;
    }

    private SQLiteDatabase openDatabase() {
        try {
            // 获得dictionary.db文件的绝对路径
            String databaseFilename = DATABASE_PATH + "/" + DATABASE_FILENAME;
            File dir = new File(DATABASE_PATH);
            // 如果/sdcard/dictionary目录中存在，创建这个目录
            if (!dir.exists())
                dir.mkdir();
            // 如果在/sdcard/dictionary目录中不存在
            // dictionary.db文件，则从res\raw目录中复制这个文件到
            // SD卡的目录（/sdcard/dictionary）
            if (!(new File(databaseFilename)).exists()) {
                // 获得封装dictionary.db文件的InputStream对象
                InputStream is = getResources().openRawResource(
                        R.raw.mobi_virus);
                FileOutputStream fos = new FileOutputStream(databaseFilename);
                byte[] buffer = new byte[8192];
                int count = 0;
                // 开始复制dictionary.db文件
                while ((count = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, count);
                }
                fos.close();
                is.close();
            }
            // 打开/sdcard/dictionary目录中的dictionary.db文件
            SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(
                    databaseFilename, null);
            return database;
        } catch (Exception e) {
        }
        return null;
    }

}