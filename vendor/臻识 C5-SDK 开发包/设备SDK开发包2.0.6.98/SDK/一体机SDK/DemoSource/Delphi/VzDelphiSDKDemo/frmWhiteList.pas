unit frmWhiteList;

interface

uses
  Windows, Messages, SysUtils, Variants, Classes, Graphics, Controls, Forms,
  Dialogs, ComCtrls, StdCtrls, ExtCtrls, VzLPRSDK;

const
    WL_QUERY_MSG   = WM_USER+104;

type
  TFormWhiteList = class(TForm)
    btnWLAdd: TButton;
    btnWLChange: TButton;
    btnWLDelete: TButton;
    edtWLPlate: TEdit;
    btnWLQuery: TButton;
    lvWL: TListView;
    procedure SetLPRHandle(mHandle: Integer);
    procedure btnWLQueryClick(Sender: TObject);
    procedure btnWLDeleteClick(Sender: TObject);
    procedure btnWLAddClick(Sender: TObject);
     procedure SearchText;
     procedure GetWLQueryMsg(var Msg: TMessage);message WL_QUERY_MSG;
    procedure btnWLChangeClick(Sender: TObject);
  private
    { Private declarations }
  public
    { Public declarations }
  end;

var
  FormWhiteList: TFormWhiteList;
  m_hLPRClient :Integer;
implementation
uses frmWLAdd, frmWLEdit ;

{$R *.dfm}

//初始化
procedure TFormWhiteList.SetLPRHandle(mHandle : Integer);
begin
  m_hLPRClient := mHandle;

  lvWL.RowSelect := True;

  lvWL.Columns.Add;
  lvWL.Columns.Add;
  lvWL.Columns.Add;
  lvWL.Columns.Add;
  lvWL.Columns.Add;
  lvWL.Columns[0].Caption := '车牌ID';
  lvWL.Columns[1].Caption := '车牌号';
  lvWL.Columns[2].Caption := '是否启用';
  lvWL.Columns[3].Caption := '过期时间';
  lvWL.Columns[4].Caption := '是否报警';
  lvWL.Columns[0].Width := 70;
  lvWL.Columns[1].Width := 90;
  lvWL.Columns[2].Width := 70;
  lvWL.Columns[3].Width := 155;
  lvWL.Columns[4].Width := 70;
end;

//白名单查询结果回调
procedure gVZLPRC_WLIST_QUERY_CALLBACK(aType:VZLPRC_WLIST_CB_TYPE; const pLP:LPVZ_LPR_WLIST_VEHICLE; const pCustomer:LPVZ_LPR_WLIST_CUSTOMER; pUserData:Pointer); stdcall;
var
  pWLResult : LPVZ_LPR_WLIST_VEHICLE;
  ret: Boolean;
begin
  GetMem(pWLResult, SizeOf(VZ_LPR_WLIST_VEHICLE));
  ZeroMemory(pWLResult,SizeOf(VZ_LPR_WLIST_VEHICLE));
  CopyMemory(pWLResult, pLP, SizeOf(VZ_LPR_WLIST_VEHICLE));

  ret := PostMessage(TFormWhiteList(pUserData).Handle, WL_QUERY_MSG,Integer(pWLResult), 0);
end;

//显示白名单查询结果
procedure TFormWhiteList.GetWLQueryMsg(var Msg: TMessage);
var
  pWLResult : LPVZ_LPR_WLIST_VEHICLE;
  struTMOverdule : string;
begin
  pWLResult := LPVZ_LPR_WLIST_VEHICLE(Msg.WParam);
  if (pWLResult <> nil) then
  begin
    struTMOverdule := Format('%d-%.2d-%.2d %.2d:%.2d:%.2d', [pWLResult.struTMOverdule.nYear, pWLResult.struTMOverdule.nMonth, pWLResult.struTMOverdule.nMDay, pWLResult.struTMOverdule.nHour, pWLResult.struTMOverdule.nMin, pWLResult.struTMOverdule.nSec]);

    with lvWL.Items.Add do
    begin
      Caption := IntToStr(pWLResult.uVehicleID);
      SubItems.Add(StrPas(pWLResult.strPlateID));
      SubItems.Add(IntToStr(pWLResult.bEnable));
      SubItems.Add(struTMOverdule);
      SubItems.Add(IntToStr(pWLResult.bAlarm));
    end;
  end;

  FreeMem(pWLResult);
end;

//查询白名单内容
procedure TFormWhiteList.SearchText();
var
  mRet: Integer;
  ret: Integer;
  strPlate : string;
  limit: VZ_LPR_WLIST_LIMIT;
  conditions: VZ_LPR_WLIST_LOAD_CONDITIONS;
  searchConstraint: VZ_LPR_WLIST_SEARCH_CONSTRAINT;
  searchWhere: VZ_LPR_WLIST_SEARCH_WHERE;
begin
    lvWL.Items.Clear;    //清空白名单列表

    strPlate := edtWLPlate.Text;
		limit.limitType := VZ_LPR_WLIST_LIMIT_TYPE_ALL;
		limit.pRangeLimit := nil;
		conditions.pLimit := @limit;
		conditions.pSortType := nil;

		StrPCopy(searchConstraint.key, 'PlateID' );
    StrPCopy(searchConstraint.search_string, edtWLPlate.Text );

		searchWhere.pSearchConstraints := @searchConstraint;
		searchWhere.searchConstraintCount := 1;
		searchWhere.searchType := 0;

		conditions.pSearchWhere := @searchWhere;

		VzLPRClient_WhiteListSetQueryCallBack( m_hLPRClient, gVZLPRC_WLIST_QUERY_CALLBACK, Self);
		mRet := VzLPRClient_WhiteListLoadVehicle(m_hLPRClient, @conditions);
end;

//查询
procedure TFormWhiteList.btnWLQueryClick(Sender: TObject);
begin
  //查询白名单内容
  SearchText();
end;

//删除白名单选中项
procedure TFormWhiteList.btnWLDeleteClick(Sender: TObject);
var
  index , mRet: Integer;
  strPlateID : string;
begin
  index := -1;
  if(lvWL.ItemIndex >= 0) then
  begin
    index := lvWL.ItemIndex;
  end;

  if(index <> -1) then
  begin
    strPlateID := lvWL.Selected.SubItems[0];
    mRet := VzLPRClient_WhiteListDeleteVehicle(m_hLPRClient, PChar(strPlateID)); //从数据库删除车辆信息

    //删除列表当前行
    lvWL.Items.Delete(index);
  end;
end;

//添加
procedure TFormWhiteList.btnWLAddClick(Sender: TObject);
var
  formWLAdd : TFormWLAdd;
begin
  formWLAdd := TFormWLAdd.Create(Application);
  formWLAdd.SetLPRHandle(m_hLPRClient);
  FormWLAdd.GetForm2(Self);
  formWLAdd.Show;
end;

//修改
procedure TFormWhiteList.btnWLChangeClick(Sender: TObject);
var
  formWLEdit : TFormWLEdit;
  index : Integer;
  bEnable, bAlarm : Integer;
  strOverdule : string;
begin
  formWLEdit := TFormWLEdit.Create(nil);
  formWLEdit.SetLPRHandle(m_hLPRClient);

  //选中列表其中项一项
  index := 0;
  if (lvWL.ItemIndex >= 0) then
  begin
    index := lvWL.ItemIndex;
  end
  else
  begin
    Exit;
  end;
  bEnable := StrToInt(lvWL.Selected.SubItems.Strings[1]);
  bAlarm := StrToInt(lvWL.Selected.SubItems.Strings[3]);

  formWLEdit.UpdateWLEditInfo(StrToInt(lvWL.Selected.Caption), lvWL.Selected.SubItems.Strings[0], bEnable, lvWL.Selected.SubItems.Strings[2], bAlarm);

  formWLEdit.GetForm2(Self);
  formWLEdit.Show;

end;

end.
