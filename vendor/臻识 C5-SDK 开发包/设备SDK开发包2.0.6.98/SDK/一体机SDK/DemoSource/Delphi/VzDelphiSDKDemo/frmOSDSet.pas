unit frmOSDSet;

interface

uses
  Windows, Messages, SysUtils, Variants, Classes, Graphics, Controls, Forms,
  Dialogs, StdCtrls, ExtCtrls, VzLPRSDK, ComCtrls;

type
  TFormOSDSet = class(TForm)
    chkOSDDay: TCheckBox;
    lblDayStyle: TLabel;
    cbbDayStyle: TComboBox;
    Label1: TLabel;
    lbl1: TLabel;
    edtOSDDayX: TEdit;
    lbl2: TLabel;
    edtOSDDayY: TEdit;
    chkOSDTime: TCheckBox;
    lbl3: TLabel;
    cbbOSDTime: TComboBox;
    lbl4: TLabel;
    lbl5: TLabel;
    lbl6: TLabel;
    edtOSDTimeX: TEdit;
    edtOSDTimeY: TEdit;
    chkOSDText: TCheckBox;
    lbl7: TLabel;
    lbl8: TLabel;
    edtOSDText: TEdit;
    lbl9: TLabel;
    lbl10: TLabel;
    edtOSDTextX: TEdit;
    edtOSDTextY: TEdit;
    chkUpdateTime: TCheckBox;
    btnOSDSave: TButton;
    btnOSDCancel: TButton;
    dtpDate: TDateTimePicker;
    dtpTime: TDateTimePicker;
    procedure SetLPRHandle(mHandle: Integer);
    procedure btnOSDSaveClick(Sender: TObject);
    procedure btnOSDCancelClick(Sender: TObject);
  private
    { Private declarations }
  public
    { Public declarations }
  end;

var
  FormOSDSet: TFormOSDSet;
  m_hLPRClient :Integer;
  osdParam :  VZ_LPRC_OSD_PARAM;

implementation

{$R *.dfm}
procedure  TFormOSDSet.SetLPRHandle(mHandle : Integer);
var
  nDayStyle, nTimeStyle, mRet : Integer;
  sOSDtext, sOSDDate, sOSDTime : string;
begin
  cbbDayStyle.Items.Add('YYYY/MM/DD');
  cbbDayStyle.Items.Add('MM/DD/YYYY');
  cbbDayStyle.Items.Add('DD/MM/YYYY');

  cbbOSDTime.Items.Add('12Hrs');
  cbbOSDTime.Items.Add('24Hrs');

  m_hLPRClient := mHandle; //获取打开设备句柄

  mRet := VzLPRClient_GetOsdParam(m_hLPRClient, @osdParam); //获取OSD参数
  //初始化OSD日期
  if (osdParam.dstampenable = 0) then
  begin
    chkOSDDay.Checked  := False;
  end
  else
  begin
    chkOSDDay.Checked  :=  True;
  end;

  //坐标
  edtOSDDayX.Text := IntToStr(osdParam.datePosX);
  edtOSDDayY.Text := IntToStr(osdParam.datePosY);

  //日期格式
  nDayStyle := osdParam.dateFormat;
  if (nDayStyle >= 0) and (nDayStyle < 3)  then
  begin
    cbbDayStyle.ItemIndex := nDayStyle;
  end
  else
  begin
    cbbDayStyle.ItemIndex := 0;
  end;

  //初始化OSD时间
  if(osdParam.tstampenable = 0) then
   begin
    chkOSDTime.Checked  := False;
  end
  else
  begin
    chkOSDTime.Checked  :=  True;
  end;

  //坐标
  edtOSDTimeX.Text := IntToStr(osdParam.timePosX);
  edtOSDTimeY.Text := IntToStr(osdParam.timePosY);

  // 时间格式
  nTimeStyle := osdParam.timeFormat;
  if(nTimeStyle = 0) or (nTimeStyle = 1 ) then
  begin
    cbbOSDTime.ItemIndex := nTimeStyle;
  end
  else
  begin
    cbbOSDTime.ItemIndex := 0;
  end;


  //初始化OSD文本
  if(osdParam.nTextEnable = 0) then
  begin
    chkOSDText.Checked  := False;
  end
  else
  begin
    chkOSDText.Checked  :=  True;
  end;

  edtOSDTextX.Text := IntToStr(osdParam.nTextPositionX);
  edtOSDTextY.Text := IntToStr(osdParam.nTextPositionY);

  //内容
  sOSDtext := StrPas(@osdParam.overlaytext[0])  ;
  edtOSDText.Text  := sOSDtext;

  //显示当前的日期和时间
  sOSDDate := FormatDateTime('ddddd',Now);
  dtpDate.Date := StrToDateTime(sOSDDate);

  sOSDTime := FormatDateTime('tt',Now);
  dtpTime.DateTime := StrToDateTime(sOSDTime);

end;

//保存设置OSD参数
procedure TFormOSDSet.btnOSDSaveClick(Sender: TObject);
var
  pDTinfo : VZ_DATE_TIME_INFO;
  nYear, nMonth, nDay, nHour, nMin, nSec, nMSec : Word;
  mRet, nRet : Integer;
begin
   //设置OSD日期
  if (chkOSDDay.Checked) then
  begin
    osdParam.dstampenable := 1;
  end
  else
  begin
    osdParam.dstampenable := 0;
  end;

  osdParam.datePosX := StrToInt(edtOSDDayX.Text);
  osdParam.datePosY := StrToInt(edtOSDDayY.Text);
  osdParam.dateFormat := cbbDayStyle.ItemIndex;

  //设置OSD时间
  if(chkOSDTime.Checked) then
  begin
    osdParam.tstampenable :=1;
  end
  else
  begin
    osdParam.tstampenable := 0;
  end;

  osdParam.timePosX := StrToInt(edtOSDTimeX.Text);
  osdParam.timePosY := StrToInt(edtOSDTimeY.Text);
  osdParam.timeFormat := cbbOSDTime.ItemIndex;

  //设置OSD文本
  if (chkOSDText.Checked) then
  begin
    osdParam.nTextEnable := 1;
  end
  else
  begin
    osdParam.nTextEnable := 0;
  end;

  osdParam.nTextPositionX := StrToInt(edtOSDTextX.Text);
  osdParam.nTextPositionY := StrToInt(edtOSDTextY.Text);

  StrCopy(@osdParam.overlaytext, PChar(edtOSDText.Text));

  mRet := VzLPRClient_SetOsdParam(m_hLPRClient, @osdParam);


  //更新时间
  if(chkUpdateTime.Checked) then
  begin
    DecodeDate(dtpDate.Date, nYear, nMonth, nDay); //解析日期控件
    DecodeTime(dtpTime.Time, nHour, nMin, nSec, nMSec); //解析时间控件

    pDTinfo.uYear := LongWord(nYear);
    pDTinfo.uMonth := LongWord(nMonth);
    pDTinfo.uDay := LongWord(nDay);
    pDTinfo.uHour := LongWord(nHour);
    pDTinfo.uMin  := LongWord(nMin);
    pDTinfo.uSec  := LongWord(nSec);

    nRet := VzLPRClient_SetDateTime(m_hLPRClient, @pDTinfo);//设置设备的日期时间
    if((mRet = 0) and (nRet = 0)) then
    begin
       ShowMessage('修改成功!');
    end
    else
    begin
      ShowMessage('修改失败!');
    end;
  end
  else
  begin
    if (mRet = 0) then
    begin
      ShowMessage('OSD参数修改成功!');
    end
    else
    begin
      ShowMessage('OSD参数修改失败!');
    end;
  end;
  
  Self.Close;
end;

//取消
procedure TFormOSDSet.btnOSDCancelClick(Sender: TObject);
begin
  Self.Close;
end;

end.
