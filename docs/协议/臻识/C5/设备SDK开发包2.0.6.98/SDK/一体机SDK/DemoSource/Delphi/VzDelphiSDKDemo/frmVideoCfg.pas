unit frmVideoCfg;

interface

uses
  Windows, Messages, SysUtils, Variants, Classes, Graphics, Controls, Forms,
  Dialogs, StdCtrls, ComCtrls, ExtCtrls, VzLPRSDK;
type
  TFormVideoCfg = class(TForm)
    pgcVideoCfg: TPageControl;
    tsMainDataRate: TTabSheet;
    tsVideoSource: TTabSheet;
    lbl1: TLabel;
    cbbframe_size: TComboBox;
    lbl2: TLabel;
    cbbframe_rate: TComboBox;
    lbl3: TLabel;
    cbbencode_type: TComboBox;
    lbl4: TLabel;
    cbbcompress_mode: TComboBox;
    lbl5: TLabel;
    cbbimg_quality: TComboBox;
    lbl6: TLabel;
    edtrateval: TEdit;
    btnOK: TButton;
    lbl7: TLabel;
    lbl8: TLabel;
    lbl9: TLabel;
    lbl10: TLabel;
    lbl11: TLabel;
    lbl12: TLabel;
    lbl13: TLabel;
    cbbvideo_standard: TComboBox;
    cbbexposure_time: TComboBox;
    cbbimg_pos: TComboBox;
    btnRecovery: TButton;
    trckbrBright: TTrackBar;
    trckbrContrast: TTrackBar;
    trckbrSaturation: TTrackBar;
    trckbrDefinition: TTrackBar;
    procedure SetLPRHandle(mHandle: Integer);
    procedure btnOKClick(Sender: TObject);
    procedure cmb_encode_type_SelectedIndexChanged(Sender: TObject);
    procedure cmb_compress_mode_SelectedIndexChanged(Sender: TObject);
    procedure btnRecoveryClick(Sender: TObject);
    procedure cbbvideo_standard_SelectedIndexChanged(Sender: TObject);
    procedure cbbexposure_time_SelectedIndexChanged(Sender: TObject);
    procedure cbbimg_pos_SelectedIndexChanged(Sender: TObject);
    procedure trckbrBrightChange(Sender: TObject);
    procedure trckbrContrastChange(Sender: TObject);
    procedure trckbrSaturationChange(Sender: TObject);
    procedure trckbrDefinitionChange(Sender: TObject);

  private
    procedure LoadVideoCfg;
    procedure LoadVideoSource;
    { Private declarations }
  public
    { Public declarations }
  end;

var
  FormVideoCfg: TFormVideoCfg;
  m_hLPRClient :Integer;

implementation

{$R *.dfm}
procedure TFormVideoCfg.SetLPRHandle(mHandle : Integer);
var
  strRate : string ;
  i :Integer;
begin
  m_hLPRClient := mHandle;

  if(m_hLPRClient <> 0) then
  begin
    //初始化主码流
    cbbframe_size.Items.Add('352x288');
    cbbframe_size.Items.Add('704x576');
    cbbframe_size.Items.Add('1280x720');
    cbbframe_size.Items.Add('1920x1080');

    for i:= 1 to 25 do
    begin
      strRate := IntToStr(i);
      cbbframe_rate.Items.Add(strRate);
    end;
     
    cbbencode_type.Items.Add('H264');
    cbbencode_type.Items.Add('JPEG');

    cbbcompress_mode.Items.Add('定码流');
    cbbcompress_mode.Items.Add('变码流');

    cbbimg_quality.Items.Add('最流畅');
    cbbimg_quality.Items.Add('较流畅');
    cbbimg_quality.Items.Add('流畅');
    cbbimg_quality.Items.Add('中等');
    cbbimg_quality.Items.Add('清晰');
    cbbimg_quality.Items.Add('较清晰');
    cbbimg_quality.Items.Add('最清晰');

    //初始化视频源
    cbbvideo_standard.Items.Add('MaxOrZero');
    cbbvideo_standard.Items.Add('50Hz');
    cbbvideo_standard.Items.Add('60Hz');

    cbbexposure_time.Items.Add('0~8ms 停车场推荐');
    cbbexposure_time.Items.Add('0~4ms ');
    cbbexposure_time.Items.Add('0~2ms 卡口推荐');

    cbbimg_pos.Items.Add('原始图像');
    cbbimg_pos.Items.Add('上下翻转');
    cbbimg_pos.Items.Add('左右翻转');
    cbbimg_pos.Items.Add('中心翻转');

     //获取视频源
    LoadVideoSource();

     //获取主码流
    LoadVideoCfg();

  end;
end;

//获取视频源
procedure TFormVideoCfg.LoadVideoSource();
var
   brt, cst, sat, hue, frequency, shutter, flip : Integer;
   mRet : Integer;
begin
   //获取视频参数
   mRet :=  VzLPRClient_GetVideoPara(m_hLPRClient, brt, cst, sat, hue);
   if (mRet = 0) then
   begin
      trckbrBright.Position := brt;
      trckbrContrast.Position := cst;
      trckbrSaturation.Position := sat;
      trckbrDefinition.Position := hue;
   end;

  //获取视频制式
  mRet := VzLPRClient_GetFrequency(m_hLPRClient, frequency);
  if(mRet = 0) then
  begin
    cbbvideo_standard.ItemIndex := frequency;
  end;


  //获取曝光时间
  mRet := VzLPRClient_GetShutter(m_hLPRClient, shutter);
  if (mRet = 0) then
  begin
    if(shutter = 2)then
    begin
      cbbexposure_time.ItemIndex := 0;
    end
    else
    if (shutter = 3) then
    begin
      cbbexposure_time.ItemIndex := 1;
    end
    else
    if(shutter = 4) then
    begin
      cbbexposure_time.ItemIndex := 2;
    end;
  end;

  //获取图像翻转
  mRet := VzLPRClient_GetFlip(m_hLPRClient, flip);
  if (mRet = 0) then
  begin
    cbbimg_pos.ItemIndex := flip;
  end;
  
end;

//获取主码流
procedure TFormVideoCfg.LoadVideoCfg();
var
  nSizeVal, nRateval, nEncodeType,  modeval, bitval, ratelist, levelval : Integer;
  mRet : Integer;
  strRateval : string ;
begin
  edtrateval.Text := strRateval;

  //获取分辨率
  mRet := VzLPRClient_GetVideoFrameSizeIndex(m_hLPRClient, nSizeVal);
  if (mRet = 0) then
  begin
    if (nSizeVal = 0) then
    begin
      cbbframe_size.ItemIndex := 1;
    end
    else
    begin
      if(nSizeVal = 1) then
      begin
       cbbframe_size.ItemIndex := 0;
      end
      else
      begin
        cbbframe_size.ItemIndex := nSizeVal;
      end;
    end;
  end;

  //获取帧率
  mRet := VzLPRClient_GetVideoFrameRate(m_hLPRClient, nRateval);
  if(mRet = 0) then
  begin
    cbbframe_rate.ItemIndex := nRateval - 1;
  end;

  //获取编码方式
  mRet := VzLPRClient_GetVideoEncodeType(m_hLPRClient, nEncodeType);
  if (mRet = 0) then
  begin
    if (nEncodeType = 0) then
    begin
      cbbencode_type.ItemIndex := 0;
      cbbcompress_mode.Enabled := True;
    end
    else
    begin
      cbbencode_type.ItemIndex := 1;
      cbbcompress_mode.Enabled := False;
    end;
  end;

  //获取码流控制
  mRet := VzLPRClient_GetVideoCompressMode(m_hLPRClient, modeval);
  if (mRet = 0) then
  begin
    cbbcompress_mode.ItemIndex := modeval;
    if (modeval = 0) then
     begin
       edtrateval.Enabled := True;
     end
    else
    begin
      edtrateval.Enabled := False;
    end;
  end;

  //获取码流上限
  mRet := VzLPRClient_GetVideoCBR(m_hLPRClient, bitval, ratelist);
  begin
     edtrateval.Text := IntToStr(bitval);
  end;

  //获取图像质量
  mRet := VzLPRClient_GetVideoVBR(m_hLPRClient, levelval);
  if (mRet = 0) then
  begin
    cbbimg_quality.ItemIndex := levelval;
  end;
end;

//设置主码流
procedure TFormVideoCfg.btnOKClick(Sender: TObject);
var
  sRateVal : string;
  nRateVal, nSizeVal, nEncodeType, modeval, level: Integer;
  mRet : Integer;
begin
  //设置分辨率
  nSizeVal := cbbframe_size.ItemIndex;
  if (nSizeVal = 0) then
  begin
    nSizeVal := 1;
  end
  else
  begin
    if (nSizeVal = 1) then
    begin
      nSizeVal := 0;
    end;
  end;
  mRet := VzLPRClient_SetVideoFrameSizeIndex(m_hLPRClient, nSizeVal);
  if(mRet <> 0) then
  begin
    ShowMessage('设置分辨率失败，请重试!');
    Exit;
  end;

  //设置帧率
  nRateVal := cbbframe_rate.ItemIndex + 1;
  mRet := VzLPRClient_SetVideoFrameRate(m_hLPRClient, nRateVal);
  if(mRet <> 0) then
  begin
    ShowMessage('设置帧率失败，请重试!');
    Exit;
  end;

  //设置编码方式
  if(cbbencode_type.ItemIndex = 0) then
  begin
    nEncodeType := 0
  end
  else
  begin
    nEncodeType := 2;
  end;
  mRet := VzLPRClient_SetVideoEncodeType(m_hLPRClient, nEncodeType);
  if (mRet <> 0) then
  begin
    ShowMessage('设置编码方式失败，请重试!');
    Exit;
  end;

  //设置码流控制
  if (cbbcompress_mode.Enabled) then
  begin
    modeval := cbbcompress_mode.ItemIndex;
    mRet := VzLPRClient_SetVideoCompressMode(m_hLPRClient, modeval);
    if (mRet <> 0) then
    begin
      ShowMessage('设置码流控制失败，请重试!');
      Exit;
    end;
  end;

  //设置图像质量
  level := cbbimg_quality.ItemIndex;
  mRet := VzLPRClient_SetVideoVBR(m_hLPRClient, level);
  if (mRet <> 0) then
  begin
      ShowMessage('设置图像质量失败，请重试!');
      Exit;
  end;

  //设置码流上限
  sRateVal := edtrateval.Text;
  nRateVal := StrToInt(sRateVal);
  if ((nRateVal <= 0) or (nRateVal > 4096)) then
  begin
    ShowMessage('码流范围为0-4096，请重新输入!');
    Exit;
  end;

  if(edtrateval.Enabled) then
  begin
    mRet := VzLPRClient_SetVideoCBR(m_hLPRClient, nRateVal);
    if (mRet <> 0) then
    begin
      ShowMessage('设置码流上限失败，请重试!');
      Exit;
    end;
  end;
  ShowMessage('设置成功 !');
end;

procedure TFormVideoCfg.cmb_encode_type_SelectedIndexChanged(Sender: TObject);
var
  nCurSel : Integer;
begin
  nCurSel := cbbencode_type.ItemIndex;
  if (nCurSel = 0) then
  begin
    cbbcompress_mode.Enabled := True;
  end
  else
  begin
    cbbcompress_mode.Enabled := False;
  end;

end;

procedure TFormVideoCfg.cmb_compress_mode_SelectedIndexChanged(Sender: TObject);
var
  nCurSel : Integer;
begin
  if(cbbcompress_mode.Enabled) then
  begin
    nCurSel := cbbcompress_mode.ItemIndex;
    if(nCurSel = 0) then
    begin
      edtrateval.Enabled := True
    end
    else
    begin
      edtrateval.Enabled := False;
    end;
  end
  else
  begin
    edtrateval.Enabled := False;
  end;

end;


//恢复默认
procedure TFormVideoCfg.btnRecoveryClick(Sender: TObject);
var
  brt, cst, sat, hue:Integer;
  mRet : Integer;
begin
  brt := 50;
  trckbrBright.Position := brt;

  cst := 40;
  trckbrContrast.Position := cst;

  sat := 30;
  trckbrSaturation.Position := sat;

  hue := 50;
  trckbrDefinition.Position := hue;

  //设置视频参数
  mRet := VzLPRClient_SetVideoPara(m_hLPRClient, brt, cst, sat, hue);

  //设置频率制式
  mRet := VzLPRClient_SetFrequency(m_hLPRClient, 1);
  cbbvideo_standard.ItemIndex := 1;

  //设置曝光时间
  mRet := VzLPRClient_SetShutter(m_hLPRClient, 3);
  cbbexposure_time.ItemIndex := 1;

  //设置图像翻转
  mRet := VzLPRClient_SetFlip(m_hLPRClient, 0);
  cbbimg_pos.ItemIndex := 0;
end;

//设置视频制式
procedure TFormVideoCfg.cbbvideo_standard_SelectedIndexChanged(
  Sender: TObject);
var
  frequency,mRet : Integer;
begin
  frequency := cbbvideo_standard.ItemIndex;
  mRet := VzLPRClient_SetFrequency(m_hLPRClient, frequency );
end;

//设置曝光时间
procedure TFormVideoCfg.cbbexposure_time_SelectedIndexChanged(Sender: TObject);
var
  shutter, curSel ,mRet : Integer;
begin
  shutter := 0;
  curSel := cbbexposure_time.ItemIndex ;
  if(curSel = 0) then
  begin
    shutter := 2;
  end
  else
  if (curSel = 1) then
  begin
    shutter := 3;
  end
  else
  if(curSel = 2) then
  begin
    shutter := 4;
  end;

  mRet := VzLPRClient_SetShutter(m_hLPRClient, shutter);
end;

//设置图像翻转
procedure TFormVideoCfg.cbbimg_pos_SelectedIndexChanged(Sender: TObject);
var
  flip, mRet: Integer;
begin
  flip := cbbimg_pos.ItemIndex;
  mRet := VzLPRClient_SetFlip(m_hLPRClient, flip);
end;

//设置亮度
procedure TFormVideoCfg.trckbrBrightChange(Sender: TObject);
var
  brt, cst, sat, hue, mRet :Integer;
begin
  brt := trckbrBright.Position;
  cst := trckbrContrast.Position;
  sat := trckbrSaturation.Position;
  hue := trckbrDefinition.Position;

  mRet := VzLPRClient_SetVideoPara(m_hLPRClient, brt, cst, sat, hue);
end;

//设置对比度
procedure TFormVideoCfg.trckbrContrastChange(Sender: TObject);
var
  brt, cst, sat, hue, mRet :Integer;
begin
  brt := trckbrBright.Position;
  cst := trckbrContrast.Position;
  sat := trckbrSaturation.Position;
  hue := trckbrDefinition.Position;

  mRet := VzLPRClient_SetVideoPara(m_hLPRClient, brt, cst, sat, hue);
end;

//设置饱和度
procedure TFormVideoCfg.trckbrSaturationChange(Sender: TObject);
var
  brt, cst, sat, hue, mRet :Integer;
begin
  brt := trckbrBright.Position;
  cst := trckbrContrast.Position;
  sat := trckbrSaturation.Position;
  hue := trckbrDefinition.Position;

  mRet := VzLPRClient_SetVideoPara(m_hLPRClient, brt, cst, sat, hue);
end;

//设置清晰度
procedure TFormVideoCfg.trckbrDefinitionChange(Sender: TObject);
var
  brt, cst, sat, hue, mRet :Integer;
begin
  brt := trckbrBright.Position;
  cst := trckbrContrast.Position;
  sat := trckbrSaturation.Position;
  hue := trckbrDefinition.Position;

  mRet := VzLPRClient_SetVideoPara(m_hLPRClient, brt, cst, sat, hue);
end;

end.
