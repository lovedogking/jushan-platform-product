unit VzDephiSDKDemoMain;

interface

uses
  Windows, Messages, SysUtils, Variants, Classes, Graphics, Controls, Forms,
  Dialogs, StdCtrls, ExtCtrls, VzLPRSDK, jpeg, ComCtrls;

const
    PLATE_INFO_MSG1 = WM_USER+101;
    PLATE_INFO_MSG2 = WM_USER+102;
    DEV_FOUND_MSG   = WM_USER+103;

type
  TFormMain = class(TForm)
    GroupBoxLogin: TGroupBox;
    lblDevIP: TLabel;
    lblUsername: TLabel;
    lblPort: TLabel;
    lblPwd: TLabel;
    edtDevIP: TEdit;
    edtPort: TEdit;
    edtUsername: TEdit;
    edtPwd: TEdit;
    btnOpen: TButton;
    btnClose: TButton;
    pnlVideo1: TPanel;
    pnlVideo2: TPanel;
    btnVideoPlay: TButton;
    btnVideoStop: TButton;
    btnManual: TButton;
    lblPlateResult1: TLabel;
    lblPlateResult2: TLabel;
    edtplate1: TEdit;
    edtPlate2: TEdit;
    imgPlate1: TImage;
    imgPlate2: TImage;
    btnRecord: TButton;
    btnRecordStop: TButton;
    btnCapture: TButton;
    btnGPIO: TButton;
    btnIsConnected: TButton;
    btnOSDCfg: TButton;
    btnBaseConfig: TButton;
    btnVideoConfig: TButton;
    btnInAndOut: TButton;
    grp1: TGroupBox;
    btnStartSearchDev: TButton;
    btnStopSearchDev: TButton;
    btnNetCfg: TButton;
    btnWhiteList: TButton;
    btnPlateQuery: TButton;
    btnRS485: TButton;
    tvFindDev: TTreeView;
    tvOpenDevice: TTreeView;
    lblUserPwd: TLabel;
    edtUserPwd: TEdit;
    btnPlayVoice: TButton;
    procedure btnOpenClick(Sender: TObject);
    procedure FromCreate(Sender: TObject);
    procedure btnVideoPlayClick(Sender: TObject);
    procedure btnManualClick(Sender: TObject);
    procedure GetPlateInfoMsg1(var Msg:TMessage);message PLATE_INFO_MSG1;
    procedure GetPlateInfoMsg2(var Msg:TMessage);message PLATE_INFO_MSG2;
    procedure btnCloseClick(Sender: TObject);
    procedure video1Play(Sender: TObject);
    procedure video2Play(Sender: TObject);
    procedure btnVideoStopClick(Sender: TObject);
    procedure btnCaptureClick(Sender: TObject);
    procedure btnRecordClick(Sender: TObject);
    procedure btnRecordStopClick(Sender: TObject);
    procedure btnIsConnectedClick(Sender: TObject);
    procedure btnGPIOClick(Sender: TObject);
    procedure btnOSDCfgClick(Sender: TObject);
    procedure btnBaseConfigClick(Sender: TObject);
    procedure btnVideoConfigClick(Sender: TObject);
    procedure btnInAndOutClick(Sender: TObject);
    procedure btnStopSearchDevClick(Sender: TObject);
    procedure btnStartSearchDevClick(Sender: TObject);
    procedure btnWhiteListClick(Sender: TObject);
    procedure btnPlateQueryClick(Sender: TObject);
    procedure btnRS485Click(Sender: TObject);
    procedure btnNetCfgClick(Sender: TObject);
    //procedure LvFindDev_Deletion(Sender: TObject; Node: TTreeNode);
    procedure FromClose(Sender: TObject; var Action: TCloseAction);
    procedure btnPlayVoiceClick(Sender: TObject);
  private
    function GetVideo1Handle: Integer;
    function GetVideo2Handle: Integer;
    function GetSeleHandle: Integer;
    procedure GetFindDevMsg(var Msg: TMessage);message DEV_FOUND_MSG;
    procedure InitImgPlate1;
    procedure InitImgPlate2;
    procedure ClearPic1;
    procedure ClearPic2;
    { Private declarations }
  public
    { Public declarations }
  end;

var
  FormMain: TFormMain;
  mPlayHandle, mPlayHandle2  : Integer;
  mFirst, bFindDev  : Boolean;
  m_sAppPath : string;
  m_sUserPwd : string;

implementation
uses frmOSDSet, frmBaseCfg, frmVideoCfg, frmInAndOut, frmWhiteList, frmPlateQuery, frmSerialControl, frmNetCfg, frmPlayVoice;

{$R *.dfm}
 //设备信息
type
    DEVICE_INFO = record
    szIPAddr : array[0..15] of Char;
    usPort1  : Word;
    usPort2  : Word;
    SL       : LongWord;
    SH       : LongWord;
end;
   PDEVICE_INFO  = ^DEVICE_INFO ;

//车牌显示信息
type
    DevPlate_INFO = record
    sPlatePath : array[0..259] of Char;
end;
   PDevPlate_INFO  = ^DevPlate_INFO ;

//打开设备
procedure TFormMain.btnOpenClick(Sender: TObject);
var
  strIP, strUser, strPwd: PChar;
  mPort: Word;
  mHandle : VzLPRClientHandle;
  index :Integer;
  pdata : LPVZ_LPRC_ACTIVE_ENCRYPT;
  mRet: Integer;
begin
  strIP := PChar(edtDevIP.Text);
  strUser := PChar(edtUsername.Text);
  strPwd := PChar(edtPwd.Text);
  mPort := StrToInt(edtPort.Text);
  m_sUserPwd := Trim(edtUserPwd.Text);

  mHandle := VzLPRClient_Open(strIP, mPort, strUser, strPwd);
  if mHandle = 0 then
  begin
    ShowMessage('设备打开失败，请检查登录信息!');
  end
  else
  begin
    tvOpenDevice.Items.AddObjectFirst(nil, strIP, TObject(mHandle)); //将IP和handle 添加到列表
    tvOpenDevice.Items[0].Selected := True;//选中当前项
  end
end;

//创建窗口
procedure TFormMain.FromCreate(Sender: TObject);
begin
  //引用DLL库
  LoadBusModule();

  //SDK初始化
  VzLPRClient_Setup();

  //初始化变量
  mFirst := True;
  m_sAppPath :=  ExtractFilePath(ParamStr(0));

  pnlVideo1.Tag := 0;
  pnlVideo2.Tag := 0;

  //初始化图片控件1背景颜色
  InitImgPlate1();

  //初始化图片控件2背景颜色
  InitImgPlate2();

end;

//初始化显示图片控件 1
procedure TFormMain.InitImgPlate1();
begin
  imgPlate1.Canvas.Pen.Width := 1;
  imgPlate1.Canvas.Pen.Color := clWhite;
  imgPlate1.Canvas.Brush.Color := clSilver;
  imgPlate1.Canvas.Pen.Mode := pmCopy;
  imgPlate1.Canvas.Rectangle(0,0,353,297);
end;

//初始化显示图片控件 2
procedure TFormMain.InitImgPlate2();
begin
  imgPlate2.Canvas.Pen.Width := 1;
  imgPlate2.Canvas.Pen.Color := clWhite;
  imgPlate2.Canvas.Brush.Color := clSilver;
  imgPlate2.Canvas.Pen.Mode := pmCopy;
  imgPlate2.Canvas.Rectangle(0,0,353,297);
end;


//获取打开设备句柄
function TFormMain.GetSeleHandle() :Integer;
var
   nHandle : Integer;
begin
   if (tvOpenDevice.Selected <> nil ) then
   begin
     nHandle := Integer(tvOpenDevice.Selected.Data);
     Result := nHandle;
   end
   else
   begin
     Result := 0;
   end;
end;

//获取视频窗口1句柄
function TFormMain.GetVideo1Handle() :Integer;
begin
  if (pnlVideo1.Tag <> 0) then
  begin
    Result := pnlVideo1.Tag;
  end
  else
  begin
    Result := 0;
  end;
end;

//获取视频窗口2句柄
function TFormMain.GetVideo2Handle() :Integer;
begin
  if (pnlVideo2.Tag <> 0) then
  begin
    Result := pnlVideo2.Tag;
  end
  else
  begin
    Result := 0;
  end;
end;

//车牌识别结果回调2
function OnPlateInfo2(mhandle: VzLPRClientHandle; pUserData: Pointer; pResult: PTH_PlateResult; uNumPlates: Word; eResultType: VZ_LPRC_RESULT_TYPE; var pImgFull: VZ_LPRC_IMAGE_INFO; var pImgPlateClip: VZ_LPRC_IMAGE_INFO): Integer; stdcall;
var
  pPResult : PTH_PlateResult;
  strFile, sPlatePath, strPlate: string;
  arrPlate : array[0..15] of Char;
  pDevPlatePath : PDevPlate_INFO;
  mRet,i: Integer;
  sUserPwd : PChar;
  bIsEncrypt : Boolean;
begin
  if (eResultType > VZ_LPRC_RESULT_REALTIME) then
  begin
    GetMem(pDevPlatePath,SizeOf(TH_PlateResult));  //开辟空间
    ZeroMemory(pDevPlatePath,SizeOf(TH_PlateResult)); //清零

    GetMem(pPResult,SizeOf(TH_PlateResult));  //开辟空间
    ZeroMemory(pPResult,SizeOf(TH_PlateResult)); //清零
    CopyMemory(pPResult, pResult, SizeOf(TH_PlateResult)); //拷贝

    strFile := ExtractFilePath(ParamStr(0))  +'Result\';
    if not DirectoryExists(strFile) then
    begin
       ForceDirectories(strFile); //创建目录
    end;

    //对车牌解密
    bIsEncrypt := True;
    for i:= 0 to 14 do
    begin
      if(pPResult.license[i] = #0)  then
      begin
        bIsEncrypt := False;
        Break;
      end;
    end;

    if(bIsEncrypt) then
    begin
      sUserPwd := PChar(m_sUserPwd);

      mRet := VzLPRClient_AesCtrDecrypt(PChar(@pPResult.license[0]), 16, sUserPwd, PChar(@arrPlate[0]), 16) ;
      if(mRet = 0)then
      begin
        strPlate := StrPas(@arrPlate[0]);
        StrCopy(PChar(@pPResult.license[0]), PChar(@arrPlate[0]));
      end;
    end
    else
    begin
       strPlate := StrPas(@pPResult.license[0]);
    end;

    sPlatePath :=  strFile + strPlate +  FormatDateTime('_yyyymmddhhnnss',Now) + '.jpg' ;

    mRet := VzLPRClient_ImageSaveToJpeg(@pImgFull, PChar(sPlatePath), 90); //将图像保存为JPEG到指定路径

    StrCopy(@pDevPlatePath.sPlatePath[0], PChar(sPlatePath));
     // 发送结果到主线程中
    PostMessage(FormMain.Handle, PLATE_INFO_MSG2,Integer(pPResult),Integer(pDevPlatePath));
  end;

end;

//车牌识别结果回调1
function OnPlateInfo1(mhandle: VzLPRClientHandle; pUserData: Pointer; pResult: PTH_PlateResult; uNumPlates: Word; eResultType: VZ_LPRC_RESULT_TYPE; var pImgFull: VZ_LPRC_IMAGE_INFO; var pImgPlateClip: VZ_LPRC_IMAGE_INFO): Integer; stdcall;
var
  pPResult : PTH_PlateResult;
  pDevPlatePath : PDevPlate_INFO;
  strFile, sPlatePath, strPlate: string;
  arrPlate : array[0..15] of Char;
  sUserPwd : PChar;
  mRet,i: Integer;
  bIsEncrypt1 : Boolean;
begin
  if (eResultType > VZ_LPRC_RESULT_REALTIME) then
  begin
    GetMem(pDevPlatePath,SizeOf(TH_PlateResult));  //开辟空间
    ZeroMemory(pDevPlatePath,SizeOf(TH_PlateResult)); //清零

    GetMem(pPResult,SizeOf(TH_PlateResult));  //开辟空间
    ZeroMemory(pPResult,SizeOf(TH_PlateResult)); //清零
    CopyMemory(pPResult, pResult, SizeOf(TH_PlateResult)); //拷贝

    strFile := ExtractFilePath(ParamStr(0))  +'Result\';
    if not DirectoryExists(strFile) then
    begin
       ForceDirectories(strFile); //创建目录
    end;

    //对车牌解密
    bIsEncrypt1 := True;
    for i:= 0 to 14 do
    begin
      if(pPResult.license[i] = #0)  then
      begin
        bIsEncrypt1 := False;
        Break;
      end;
    end;

    if(bIsEncrypt1) then
    begin
      sUserPwd := PChar(m_sUserPwd);

      mRet := VzLPRClient_AesCtrDecrypt(PChar(@pPResult.license[0]), 16, sUserPwd, PChar(@arrPlate[0]), 16) ;
      if(mRet = 0)then
      begin
        StrCopy(PChar(@pPResult.license[0]), PChar(@arrPlate[0]));
        strPlate := StrPas(@arrPlate[0]);
      end;
    end
    else
    begin
       strPlate := StrPas(@pPResult.license[0]);
    end;

    sPlatePath :=  strFile + strPlate +  FormatDateTime('_yyyymmddhhnnss',Now) + '.jpg' ;

    mRet := VzLPRClient_ImageSaveToJpeg(@pImgFull, PChar(sPlatePath), 90); //将图像保存为JPEG到指定路径

    StrCopy(@pDevPlatePath.sPlatePath[0], PChar(sPlatePath));
     // 发送结果到主线程中
    PostMessage(FormMain.Handle, PLATE_INFO_MSG1,Integer(pPResult),Integer(pDevPlatePath));
  end;
end;

//显示车牌识别结果 1
procedure TFormMain.GetPlateInfoMsg1(var Msg: TMessage);
var
  pDevPlatePath: PDevPlate_INFO;
  jpegImgPlate : TJPEGImage;
  pPResult : PTH_PlateResult;
  strFilePath : string;
begin
  pPResult :=  PTH_PlateResult(Msg.WParam);
  pDevPlatePath := PDevPlate_INFO(Msg.LParam);

  //显示车牌号
  edtplate1.Text := pPResult.license;

  //显示车牌识别图片
  strFilePath := StrPas(@pDevPlatePath.sPlatePath[0]);
  if (Trim(strFilePath) <> '') then
  begin
    jpegImgPlate := TJPEGImage.Create;  //创建JPEG图像对象

     try
      jpegImgPlate.LoadFromFile(strFilePath);

      imgPlate1.Picture.Graphic := jpegImgPlate;

    finally
      jpegImgPlate.Free;
    end;

  end;

  FreeMem(pPResult);  //释放内存
  FreeMem(pDevPlatePath);
end;

//显示车牌识别结果 2
procedure TFormMain.GetPlateInfoMsg2(var Msg: TMessage);
var
  pDevPlatePath2: PDevPlate_INFO;
  strFile, strPlate: string;
  jpegImgPlate : TJPEGImage;
  pPResult2 : PTH_PlateResult;
begin
  pPResult2 := PTH_PlateResult(Msg.WParam);
  pDevPlatePath2   := PDevPlate_INFO(Msg.LParam);

  //显示车牌号
  edtPlate2.Text := pPResult2.license;

   //显示车牌识别图片
  strFile := StrPas(@pDevPlatePath2.sPlatePath[0]);
  if (Trim(strFile) <> '') then
  begin
    jpegImgPlate := TJPEGImage.Create;  //创建JPEG图像对象
    try
      jpegImgPlate.LoadFromFile(strFile);

      imgPlate2.Picture.Graphic := jpegImgPlate;

    finally
      jpegImgPlate.Free;
    end;
  end;

  FreeMem(pPResult2);  //释放内存
  FreeMem(pDevPlatePath2);
end;

//视频播放
procedure TFormMain.btnVideoPlayClick(Sender: TObject);
var
  lprHandle, lprHandle1, lprHandle2  :Integer;
begin
    lprHandle := GetSeleHandle();
    if lprHandle = 0 then
    begin
      ShowMessage('请选择一台列表中的设备!');
      Exit;
    end
    else
    begin
      if((lprHandle = GetVideo1Handle()) or (lprHandle = GetVideo2Handle()))  then
      begin
        ShowMessage('该设备已经输出在视频窗口，请先停止!');
        Exit;
      end;

      if mFirst = True then
      begin
        if(mPlayHandle >0) then
        begin
          VzLPRClient_StopRealPlay(mPlayHandle);  //关闭当前播放的视频
          mPlayHandle := 0;
        end;

        lprHandle1 := GetVideo1Handle();
        if (lprHandle1 > 0) then
        begin
          VzLPRClient_SetPlateInfoCallBack(lprHandle1, nil, nil, 0);
        end;

        VzLPRClient_SetPlateInfoCallBack(lprHandle, OnPlateInfo1, Self, 1);
        mPlayHandle := VzLPRClient_StartRealPlay(lprHandle, pnlVideo1.Handle); //播放实时视频
        pnlVideo1.Tag := lprHandle;
      end
      else
      begin
        if(mPlayHandle2 > 0) then
        begin
          VzLPRClient_StopRealPlay(mPlayHandle2);
          mPlayHandle2 := 0;
        end;

        lprHandle2 := GetVideo2Handle();
        if (lprHandle2 > 0) then
        begin
          VzLPRClient_SetPlateInfoCallBack(lprHandle2, nil, nil, 0);
        end;

        VzLPRClient_SetPlateInfoCallBack(lprHandle, OnPlateInfo2, Self, 1);
        mPlayHandle2 := VzLPRClient_StartRealPlay(lprHandle, pnlVideo2.Handle); //播放实时视频
        pnlVideo2.Tag := lprHandle;
      end;
    end;
end;

//手动识别
procedure TFormMain.btnManualClick(Sender: TObject);
var
  lprHandle,ret : Integer;
begin
  lprHandle := GetSeleHandle();
  if lprHandle = 0 then
  begin
    ShowMessage('请选择一台列表中的设备!');
    Exit;
  end;

  if lprHandle > 0 then
  begin
    ret := VzLPRClient_ForceTrigger(lprHandle);
  end;

end;

//关闭设备
procedure TFormMain.btnCloseClick(Sender: TObject);
var
  lprHandle : Integer;
begin
  lprHandle := GetSeleHandle();
  if lprHandle = 0 then
  begin
    ShowMessage('请选择一台列表中的设备!');
    Exit;
  end;

  if(lprHandle = GetVideo1Handle) then
  begin
    if (mPlayHandle > 0) then
      begin
        VzLPRClient_StopRealPlay(mPlayHandle);
        mPlayHandle := 0;
      end;

      ClearPic1();
  end
  else
  begin
    if(mPlayHandle2 > 0) then
    begin
      VzLPRClient_StopRealPlay(mPlayHandle2);
      mPlayHandle2 := 0;
    end;

    ClearPic2();
  end;

  VzLPRClient_Close(lprHandle); //关闭设备

  tvOpenDevice.Selected.Delete; //删除列表打开的设备
end;

//点击视频控件1事件
procedure TFormMain.video1Play(Sender: TObject);
begin
  pnlVideo1.Color :=  clGray;
  pnlVideo2.Color := clSilver;
  mFirst := True;
end;

//点击视频控件2事件
procedure TFormMain.video2Play(Sender: TObject);
begin
  pnlVideo2.Color :=  clGray;
  pnlVideo1.Color := clSilver;
  mFirst := False;
end;

//清空图片1
procedure  TFormMain.ClearPic1();
begin
  pnlVideo1.Color := clSilver;
  pnlVideo1.Refresh;
  pnlVideo1.Tag := 0;

  edtplate1.Text := '';
  imgPlate1.Picture := nil;  //清空图片

  //设置图片控件1背景色
  InitImgPlate1();
end;

//清空图片2
procedure  TFormMain.ClearPic2();
begin
  pnlVideo2.Color := clSilver;
  pnlVideo2.Refresh;
  pnlVideo2.Tag := 0;

  edtplate2.Text := '';
  imgPlate2.Picture := nil;  //清空图片

  //设置图片控件1背景色
  InitImgPlate2();
end;

//停止播放
procedure TFormMain.btnVideoStopClick(Sender: TObject);
var
  lprHandle, lprHandle1, lprHandle2 : Integer;
begin
  lprHandle := GetSeleHandle();
  if lprHandle = 0 then
  begin
    ShowMessage('请选择一台列表中的设备!');
    Exit;
  end;

  if(mFirst) then
  begin
    if (mPlayHandle > 0) then
    begin
      VzLPRClient_StopRealPlay(mPlayHandle);
      mPlayHandle := 0;
    end;

    lprHandle1 := GetVideo1Handle();
    if (lprHandle1 > 0) then
    begin
      VzLPRClient_SetPlateInfoCallBack(lprHandle1, nil, nil, 0);
    end;

    //清空图片1
    ClearPic1();
  end
  else
  begin
    if(mPlayHandle2 > 0) then
    begin
      VzLPRClient_StopRealPlay(mPlayHandle2);
      mPlayHandle2 := 0;
    end;

    lprHandle2 := GetVideo2Handle();
    if(lprHandle2 > 0) then
    begin
      VzLPRClient_SetPlateInfoCallBack(lprHandle2, nil, nil, 0);
    end;
  
     //清空图片2
    ClearPic2();

  end;
end;

//抓图
procedure TFormMain.btnCaptureClick(Sender: TObject);
var
  sFilePath ,sPlatePath : string;
  mRet :Integer;
begin
  sFilePath := ExtractFilePath(ParamStr(0)) + 'Snap\';
  if not DirectoryExists(sFilePath) then
  begin
    ForceDirectories(sFilePath);
  end;
  sPlatePath := sFilePath + FormatDateTime('yyyymmddhhnnss',Now) + '.jpg';
  mRet := -1;
  if(mFirst = True) then
  begin
    if(mPlayHandle > 0)  then
    begin
      mRet := VzLPRClient_GetSnapShootToJpeg2(mPlayHandle, PChar(sPlatePath), 80); //保存正在播放的视频的当前帧的截图到指定路径
    end;
  end
  else
  begin
    if(mPlayHandle2 > 0) then
    begin
      mRet := VzLPRClient_GetSnapShootToJpeg2(mPlayHandle2, PChar(sPlatePath), 80)
    end;
  end;

  if (mRet = 0) then
  begin
    ShowMessage('截图成功 !');
  end
  else
  begin
    ShowMessage('截图失败，请检查视频是否播放 !');
  end;
end;

//开始录像
procedure TFormMain.btnRecordClick(Sender: TObject);
var
  lprHandle ,ret: Integer;
  sFilePath,sRecordPath : string;
begin
  lprHandle := GetSeleHandle();
  if lprHandle = 0 then
  begin
    ShowMessage('请选择一台列表中的设备!');
    Exit;
  end;

  sFilePath := ExtractFilePath(ParamStr(0)) + 'Video\' ;
 if not DirectoryExists(sFilePath) then
  begin
    ForceDirectories(sFilePath);
  end;
                
  sRecordPath := sFilePath + FormatDateTime('yyyymmddhhmmss',Now) + '.avi';
  ret := VzLPRClient_SaveRealData(lprHandle, PChar(sRecordPath));
  
  btnRecord.Enabled := False;
  btnRecordStop.Enabled := True;
end;

//停止录像
procedure TFormMain.btnRecordStopClick(Sender: TObject);
var
  lprHandle ,ret : Integer;
begin
  lprHandle := GetSeleHandle();
  if lprHandle = 0 then
  begin
    ShowMessage('请选择一台列表中的设备!');
    Exit;
  end;

  VzLPRClient_StopSaveRealData(lprHandle);  //停止录像

  btnRecord.Enabled := True;
  btnRecordStop.Enabled := False;
   
end;

//连接状态
procedure TFormMain.btnIsConnectedClick(Sender: TObject);
var
  lprHandle ,mRet: Integer;
  cState : Byte;
begin
  lprHandle := GetSeleHandle();
  if lprHandle = 0 then
  begin
    ShowMessage('请选择一台列表中的设备!');
    Exit;
  end;

  mRet := VzLPRClient_IsConnected(lprHandle,cState); //获取连接状态
  if (cState = 1) then
  begin
    ShowMessage('连接正常!');
  end
  else
  begin
    ShowMessage('连接失败!');
  end;
end;

//GPIO
procedure TFormMain.btnGPIOClick(Sender: TObject);
var
  lprHandle,nValue : Integer;
  mRet :Integer;
begin
  lprHandle := GetSeleHandle();
  if lprHandle = 0 then
  begin
    ShowMessage('请选择一台列表中的设备!');
    Exit;
  end;

  mRet := VzLPRClient_GetGPIOValue(lprHandle, 0, nValue);//获取GPIO的状态
  if nValue = 0 then
  begin
    ShowMessage('IOIput is [闭路]');
  end
  else
  begin
    if nValue = 1 then
    begin
      ShowMessage('IOIput is [开路]');
    end;
  end;
end;

//OSD配置
procedure TFormMain.btnOSDCfgClick(Sender: TObject);
var
  lprHandle : Integer;
  fromOSDSet : TFormOSDSet;
begin
  lprHandle := GetSeleHandle();
  if lprHandle = 0 then
  begin
    ShowMessage('请选择一台列表中的设备!');
    Exit;
  end;

  fromOSDSet := TFormOSDSet.Create(Application);
  fromOSDSet.SetLPRHandle(lprHandle);
  fromOSDSet.Show;
end;

//基本配置
procedure TFormMain.btnBaseConfigClick(Sender: TObject);
var
  lprHandle : Integer;
  fromBaseCfg :TFormBaseCfg;
begin
  lprHandle := GetSeleHandle();
  if lprHandle = 0 then
  begin
    ShowMessage('请选择一台列表中的设备!');
    Exit;
  end;

  fromBaseCfg := TFormBaseCfg.Create(Application);
  fromBaseCfg.SetLPRHandle(lprHandle);
  fromBaseCfg.Show;
end;

//视频配置
procedure TFormMain.btnVideoConfigClick(Sender: TObject);
var
  lprHandle : Integer;
  fromVideoCfg :TFormVideoCfg;
begin
   lprHandle := GetSeleHandle();
  if lprHandle = 0 then
  begin
    ShowMessage('请选择一台列表中的设备!');
    Exit;
  end;

  fromVideoCfg := TFormVideoCfg.Create(Application);
  fromVideoCfg.SetLPRHandle(lprHandle);
  fromVideoCfg.Show;
end;

//输入输出
procedure TFormMain.btnInAndOutClick(Sender: TObject);
var
  lprHandle : Integer;
  fromInAndOut : TFormInAndOut;
begin
   lprHandle := GetSeleHandle();
  if lprHandle = 0 then
  begin
    ShowMessage('请选择一台列表中的设备!');
    Exit;
  end;

  fromInAndOut := TFormInAndOut.Create(Application);
  fromInAndOut.SetLPRHandle(lprHandle);
  fromInAndOut.Show;
end;

//停止查找
procedure TFormMain.btnStopSearchDevClick(Sender: TObject);
var
  mRet : Integer;
begin
  mRet := VZLPRClient_StopFindDevice();
  bFindDev := False;
end;

//查找设备回调
function OnDevFound(pStrDevName: PChar; pStrIPAddr: PChar; usPort1: Word; usPort2: Word; SL : LongWord; SH : LongWord; pUserData: Pointer): Integer; stdcall;
var
  pDeviceInfo : PDEVICE_INFO;
begin
  if ((pStrIPAddr <> nil) and (usPort1 > 0)) then
  begin
     GetMem(pDeviceInfo, SizeOf(DEVICE_INFO)); //开辟空间
     ZeroMemory(pDeviceInfo,SizeOf(DEVICE_INFO));//清零

     StrCopy(@pDeviceInfo.szIPAddr, pStrIPAddr);
     pDeviceInfo.usPort1 := usPort1;
     pDeviceInfo.usPort2 := usPort2;
     pDeviceInfo.SL := SL;
     pDeviceInfo.SH := SH;

     PostMessage(FormMain.Handle, DEV_FOUND_MSG ,Integer(pDeviceInfo), 0);
  end;
end;

//显示查找结果
procedure TFormMain.GetFindDevMsg(var Msg: TMessage);
var
  pDevInfo : PDEVICE_INFO;
begin
  pDevInfo := PDEVICE_INFO(Msg.WParam);
  if (pDevInfo <> nil) then
  begin
    tvFindDev.Items.AddObject(nil, StrPas(pDevInfo.szIPAddr), TObject(pDevInfo));
  end;

  FreeMem(pDevInfo);
end;

//开始查找
procedure TFormMain.btnStartSearchDevClick(Sender: TObject);
var
  mRet : Integer;
begin
  mRet := VZLPRClient_StopFindDevice();
  bFindDev := True;

  tvFindDev.Items.Clear;  //清空列表

  VZLPRClient_StartFindDevice(OnDevFound, Self);
end;

//修改IP
procedure TFormMain.btnNetCfgClick(Sender: TObject);
var
  formNetCfg : TFormNetCfg;
  SL, SH : LongWord;
  IP : string;
begin
  if(tvFindDev.SelectionCount <= 0) then
  begin
    ShowMessage('请选择一台设备');
    Exit;
  end;
  SL := 0;
  SH := 0;
  IP := tvFindDev.Selected.Text;
  SL := PDEVICE_INFO(tvFindDev.Selected.Data)^.SL;
  SH := PDEVICE_INFO(tvFindDev.Selected.Data)^.SH;

  formNetCfg := TFormNetCfg.Create(Application);
  formNetCfg.SetNetParam(IP, SL, SH);
  formNetCfg.Show;
end;

//procedure TFormMain.LvFindDev_Deletion(Sender: TObject; Node: TTreeNode);
//begin
  //释放内存
  //Dispose(PDEVICE_INFO(Node.Data));
//end;

//白名单
procedure TFormMain.btnWhiteListClick(Sender: TObject);
var
  lprHandle : Integer;
  fromWhiteList : TFormWhiteList;
begin
  lprHandle := GetSeleHandle();
  if lprHandle = 0 then
  begin
    ShowMessage('请选择一台列表中的设备!');
    Exit;
  end;

  fromWhiteList := TFormWhiteList.Create(Application);
  fromWhiteList.SetLPRHandle(lprHandle);
  fromWhiteList.Show;
end;

//车牌查询
procedure TFormMain.btnPlateQueryClick(Sender: TObject);
var
  lprHandle : Integer;
  fromPlateQuery : TFormPlateQuery;
begin
  lprHandle := GetSeleHandle();
  if lprHandle = 0 then
  begin
    ShowMessage('请选择一台列表中的设备!');
    Exit;
  end;

  fromPlateQuery := TFormPlateQuery.Create(Application);
  fromPlateQuery.SetLPRHandle(lprHandle, m_sAppPath);
  fromPlateQuery.Show;
end;

//485控制
procedure TFormMain.btnRS485Click(Sender: TObject);
var
  lprHandle : Integer;
  fromSerialCtl : TFormSerialControl;
begin
  lprHandle := GetSeleHandle();
  if lprHandle = 0 then
  begin
    ShowMessage('请选择一台列表中的设备!');
    Exit;
  end;

  fromSerialCtl := TFormSerialControl.Create(Application);
  fromSerialCtl.SetLPRHandle(lprHandle);
  fromSerialCtl.Show;
end;

//关闭主窗口
procedure TFormMain.FromClose(Sender: TObject; var Action: TCloseAction);
begin
  Action  := caFree;
  //关闭视频1
  if(mPlayHandle > 0)then
  begin
    VzLPRClient_StopRealPlay(mPlayHandle);
    mPlayHandle := 0;
  end;

  //关闭视频2
  if(mPlayHandle2 > 0)then
  begin
    VzLPRClient_StopRealPlay(mPlayHandle2);
    mPlayHandle2 := 0;
  end;

  
  //停止查找
  if(bFindDev = True) then
  begin
    VZLPRClient_StopFindDevice();
    bFindDev := False;
  end;
  
  //SDK释放全局
  VzLPRClient_Cleanup();


end;


procedure TFormMain.btnPlayVoiceClick(Sender: TObject);
var
  lprHandle : Integer;
  fromPlayVoice : TFormPlayVoice;
begin
  lprHandle := GetSeleHandle();
  if lprHandle = 0 then
  begin
    ShowMessage('请选择一台列表中的设备!');
    Exit;
  end;

  fromPlayVoice := TFormPlayVoice.Create(Application);
  fromPlayVoice.SetLPRHandle(lprHandle);
  fromPlayVoice.Show;
end;

end.
