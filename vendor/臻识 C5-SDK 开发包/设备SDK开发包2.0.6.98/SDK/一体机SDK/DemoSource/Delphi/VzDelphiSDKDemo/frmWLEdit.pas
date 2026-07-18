unit frmWLEdit;

interface

uses
  Windows, Messages, SysUtils, Variants, Classes, Graphics, Controls, Forms,
  Dialogs, ComCtrls, StdCtrls, VzLPRSDK, frmWhiteList;

type
  TFormWLEdit = class(TForm)
    lbl1: TLabel;
    lbl2: TLabel;
    lbl3: TLabel;
    lbl4: TLabel;
    btnWLOK: TButton;
    btnWLCancel: TButton;
    edtWLPlate: TEdit;
    chkIsEnable: TCheckBox;
    chkIsAlarm: TCheckBox;
    dtpWLDate: TDateTimePicker;
    dtpWLTime: TDateTimePicker;
    procedure SetLPRHandle(mHandle: Integer);
    procedure GetForm2(form: TFormWhiteList);
    procedure UpdateWLEditInfo(lVehicleID: Integer; PlateID: string;
      bEnable: Integer; strOverdule: string; bAlarm: Integer);
    procedure btnWLCancelClick(Sender: TObject);
    procedure btnWLOKClick(Sender: TObject);
    procedure ShowView(PlateID: string; bEnable: Integer;
      strOverdule: string; bAlarm: Integer);
  private
    { Private declarations }
  public
    { Public declarations }
  end;

var
  FormWLEdit: TFormWLEdit;
  wlistVehicle : VZ_LPR_WLIST_VEHICLE;
  m_hLPRClient :Integer;
  Form2 : TFormWhiteList;
  Update_lVehicleID : Integer;
implementation

{$R *.dfm}
procedure TFormWLEdit.SetLPRHandle(mHandle : Integer);
begin
  m_hLPRClient := mHandle;
end;

procedure TFormWLEdit.GetForm2(form : TFormWhiteList);
begin
  Form2 := form;
end;

procedure TFormWLEdit.UpdateWLEditInfo(lVehicleID:Integer; PlateID:string; bEnable:Integer; strOverdule:string; bAlarm:Integer);
begin
  Update_lVehicleID := lVehicleID;

  if (strOverdule <> '') then
  begin
    //显示白名单选中信息
    ShowView(PlateID, bEnable, strOverdule, bAlarm);
  end;
end;

//显示白名单选中信息
procedure TFormWLEdit.ShowView(PlateID:string; bEnable:Integer; strOverdule:string; bAlarm:Integer);
var
  Stl : TStrings;
  strWLDate, strWLTime : string;
begin
  edtWLPlate.Text := PlateID;

  if (bEnable = 1) then
  begin
    chkIsEnable.Checked := True;
  end
  else
  begin
    chkIsEnable.Checked := False;
  end;

  if(bAlarm = 1) then
  begin
    chkIsAlarm.Checked := True;
  end
  else
  begin
    chkIsAlarm.Checked := False;
  end;

  strWLDate := Copy(strOverdule, 0, 10);
  strWLTime := Copy(strOverdule, 13, 9);
  strWLDate := StringReplace(strWLDate,'_','/',[rfReplaceAll]);    //将2016_10_01替换成2016/10/01

  dtpWLDate.Date := StrToDateTime(strWLDate);
  dtpWLTime.Time := StrToDateTime(strWLTime);

end;

//取消
procedure TFormWLEdit.btnWLCancelClick(Sender: TObject);
begin
  Self.Close;
end;

//保存修改白名单
procedure TFormWLEdit.btnWLOKClick(Sender: TObject);
var
  nYear, nMon, nDay, nHour, nMin, nSec, nMSec: Word;
  mRet : Integer;
begin
  ZeroMemory(@wlistVehicle, SizeOf(VZ_LPR_WLIST_VEHICLE));

  wlistVehicle.uVehicleID := Update_lVehicleID;
  if(chkIsEnable.Checked) then
  begin
    wlistVehicle.bEnable := 1;
  end
  else
  begin
    wlistVehicle.bEnable := 0;
  end;

  if(chkIsAlarm.Checked) then
  begin
    wlistVehicle.bAlarm := 1;
  end
  else
  begin
    wlistVehicle.bAlarm := 0;
  end;

  wlistVehicle.uCustomerID := 1;

  CopyMemory(@wlistVehicle.strPlateID, PChar(edtWLPlate.Text), Length(edtWLPlate.Text));

  DecodeDate(dtpWLDate.Date, nYear, nMon, nDay);
  DecodeTime(dtpWLTime.DateTime,nHour, nMin, nSec, nMSec);

  wlistVehicle.struTMOverdule.nYear := nYear;
  wlistVehicle.struTMOverdule.nMonth := nMon;
  wlistVehicle.struTMOverdule.nMDay := nDay;
  wlistVehicle.struTMOverdule.nHour := nHour;
  wlistVehicle.struTMOverdule.nMin := nMin;
  wlistVehicle.struTMOverdule.nSec := nSec;
  wlistVehicle.bUsingTimeSeg := 1;
  wlistVehicle.bEnableTMOverdule := 1;

  mRet := VzLPRClient_WhiteListUpdateVehicleByID(m_hLPRClient, @wlistVehicle); //往白名单表中更新一个车辆信息

  //显示修改白名单查询结果
  Form2.SearchText();

  Self.Close;
end;

end.
