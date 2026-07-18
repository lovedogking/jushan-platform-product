unit frmWLAdd;

interface

uses
  Windows, Messages, SysUtils, Variants, Classes, Graphics, Controls, Forms,
  Dialogs, ComCtrls, StdCtrls, VzLPRSDK, frmWhiteList;

type
  TFormWLAdd = class(TForm)
    lbl1: TLabel;
    edtWLPlate: TEdit;
    lbl2: TLabel;
    lbl3: TLabel;
    lbl4: TLabel;
    chkisenable: TCheckBox;
    chkisalarm: TCheckBox;
    dtpdatalist: TDateTimePicker;
    btnWLAddOK: TButton;
    btnWLCancel: TButton;
    dtpWLAddtime: TDateTimePicker;
    procedure SetLPRHandle(mHandle: Integer);
    procedure GetForm2(form: TFormWhiteList);
    procedure btnWLAddOKClick(Sender: TObject);
    procedure btnWLCancelClick(Sender: TObject);
  private
    { Private declarations }
  public
    { Public declarations }
  end;

var
  FormWLAdd: TFormWLAdd;
  m_hLPRClient :Integer;
  wlistVehicle : VZ_LPR_WLIST_VEHICLE;
  wlistRow: VZ_LPR_WLIST_ROW;
  importResult:VZ_LPR_WLIST_IMPORT_RESULT;
  From2 : TFormWhiteList;

implementation


{$R *.dfm}
procedure TFormWLAdd.SetLPRHandle(mHandle : Integer);
var
  strWLDate, strWLTime : string;
begin
  m_hLPRClient := mHandle;

  //显示当前时间
  strWLDate := FormatDateTime('ddddd',Now);
  dtpdatalist.Date := StrToDateTime(strWLDate);

  strWLTime := FormatDateTime('tt', Now);
  dtpWLAddtime.DateTime := StrToDateTime(strWLTime);

end;

procedure TFormWLAdd.GetForm2(form : TFormWhiteList);
begin
  From2 := form;
end;

//保存添加
procedure TFormWLAdd.btnWLAddOKClick(Sender: TObject);
var
   nYear, nMon, nDay, nHour, nMin, nSec, nMsec : Word;
   strComment : string;
   mRet : Integer;
begin
  //清零
  ZeroMemory(@wlistVehicle, sizeof(VZ_LPR_WLIST_VEHICLE));
  ZeroMemory(@wlistRow, sizeof(VZ_LPR_WLIST_ROW) );
  ZeroMemory(@importResult, sizeof(VZ_LPR_WLIST_IMPORT_RESULT) );

  //是否报警
  if(chkisenable.Checked) then
  begin
    wlistVehicle.bEnable := 1;
  end
  else
  begin
    wlistVehicle.bEnable := 0;
  end;

  //是否启用白名单
  if(chkisalarm.Checked) then
  begin
    wlistVehicle.bAlarm := 1;
  end
  else
  begin
    wlistVehicle.bAlarm := 0;
  end;

  wlistVehicle.uCustomerID := 1; //客户ID
  wlistVehicle.bUsingTimeSeg := 1;    //是否启用时间段
  wlistVehicle.bEnableTMOverdule := 1; //是否启用过期时间
  wlistVehicle.iColor := 0;
  wlistVehicle.iPlateType := 0;

  strComment := ' ';
  CopyMemory(@wlistVehicle.strCode[0], PChar(edtWLPlate.Text), Length(edtWLPlate.Text));
  CopyMemory(@wlistVehicle.strPlateID[0], PChar(edtWLPlate.Text), Length(edtWLPlate.Text));

  //解析日期、时间
  DecodeDate(dtpdatalist.Date, nYear, nMon, nDay );
  DecodeTime(dtpWLAddtime.DateTime, nHour, nMin, nSec, nMsec);

  //过期时间
  wlistVehicle.struTMOverdule.nYear := nYear;
  wlistVehicle.struTMOverdule.nMonth := nMon;
  wlistVehicle.struTMOverdule.nMDay := nDay;
  wlistVehicle.struTMOverdule.nHour := nHour;
  wlistVehicle.struTMOverdule.nMin := nMin;
  wlistVehicle.struTMOverdule.nSec := nSec;

  wlistRow.pVehicle := @wlistVehicle;

  mRet := VzLPRClient_WhiteListImportRows(m_hLPRClient, 1, @wlistRow, @importResult); //向白名单表导入客户和车辆记录

  //显示修改白名单查询结果
  From2.SearchText();

  Self.Close;
end;

//取消
procedure TFormWLAdd.btnWLCancelClick(Sender: TObject);
begin
  Self.Close;
end;

end.
