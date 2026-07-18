unit frmPlateQuery;

interface

uses
  Windows, Messages, SysUtils, Variants, Classes, Graphics, Controls, Forms,
  Dialogs, StdCtrls, ComCtrls, VzLPRSDK, ExtCtrls , jpeg;
const
    PLATE_Query_MSG = WM_USER+105;

type
  TFormPlateQuery = class(TForm)
    lbl1: TLabel;
    dtpstarttimehour: TDateTimePicker;
    lbl2: TLabel;
    dtpendtimeday: TDateTimePicker;
    dtpendtimehour: TDateTimePicker;
    lbl3: TLabel;
    edtPlateQuery: TEdit;
    btnQuery: TButton;
    lvQueryResult: TListView;
    btnprepage: TButton;
    btnnextpage: TButton;
    dtpstarttimeday: TDateTimePicker;
    lblPageMsg: TLabel;
    procedure SetLPRHandle(mhandle: Integer; picPath:string);
    procedure FromPlateQuery_Create(Sender: TObject);
    procedure btnQueryClick(Sender: TObject);
    procedure btnprepageClick(Sender: TObject);
    procedure btnnextpageClick(Sender: TObject);
    procedure QueryResult_DbClick(Sender: TObject);

  private

    procedure ListViewClear;
    procedure QueryByPage(nPageIndex: Integer);
    procedure PlatePageMsgShow;
    procedure PlateQueryResult(var Msg: TMessage);message PLATE_Query_MSG;

    { Private declarations }
  public
    { Public declarations }
  end;

var
  FormPlateQuery: TFormPlateQuery;
  m_hLPRClient :Integer;
  m_nTotalCount , m_nCurPage, m_nPageCount: Integer;
  m_strStartTime , m_strEndTime, m_strPageMsg, m_strPlate : string;
  ONE_PAGE_COUNT : Integer;
  m_pBufJeg : PByte;
  m_uSizeBufJpg : Integer;
  txtPicPath : string;

implementation
uses frmPlateShow;

{$R *.dfm}
//车牌查询结果回调
function gOnQueryPlateInfo(mhandle: VzLPRClientHandle; pUserData: Pointer;  pResult: PTH_PlateResult; uNumPlates: Word; eResultType: VZ_LPRC_RESULT_TYPE; var pImgFull: VZ_LPRC_IMAGE_INFO; var pImgPlateClip: VZ_LPRC_IMAGE_INFO): Integer; stdcall;
var
  pPResult : PTH_PlateResult;
begin
  if( pUserData <> nil ) then
  begin
    GetMem(pPResult, SizeOf(TH_PlateResult));
    ZeroMemory(ppResult, SizeOf(TH_PlateResult));
    CopyMemory(pPResult,pResult,SizeOf(TH_PlateResult));

    PostMessage(TFormPlateQuery(pUserData).Handle, PLATE_Query_MSG, Integer(pPResult) , Ord(eResultType)) ;
  end;
end;

//显示车牌查询结果
procedure TFormPlateQuery.PlateQueryResult(var Msg: TMessage);
var
  pResult : PTH_PlateResult;
  eType : VZ_LPRC_RESULT_TYPE;
  strPlateID, szTime, strResultType, strPlateColor: string;
begin
  pResult := PTH_PlateResult(Msg.WParam);
  eType := VZ_LPRC_RESULT_TYPE(Msg.LParam);
  if(pResult <> nil) then
  begin
    strPlateID := '';
    szTime := '';
    strResultType := '';

    //车牌号
    strPlateID := IntToStr(pResult.uId);

    //识别时间
    szTime := Format('%d-%.2d-%.2d %.2d:%.2d:%.2d',[pResult.struBDTime.bdt_year,pResult.struBDTime.bdt_mon,pResult.struBDTime.bdt_mday,pResult.struBDTime.bdt_hour,pResult.struBDTime.bdt_min,pResult.struBDTime.bdt_sec]);

    //触发类型
    case eType of
      VZ_LPRC_RESULT_STABLE : strResultType := '稳定结果';
      VZ_LPRC_RESULT_FORCE_TRIGGER: strResultType := '手动触发';
      VZ_LPRC_RESULT_IO_TRIGGER : strResultType := '地感触发';
      VZ_LPRC_RESULT_VLOOP_TRIGGER : strResultType := '虚拟线圈';
    end;

    //车牌颜色
    case pResult.nColor of
      LC_UNKNOWN : strPlateColor := '未知';
      LC_BLUE : strPlateColor := '蓝色' ;
      LC_YELLOW : strPlateColor := '黄色';
      LC_WHITE : strPlateColor := '白色';
      LC_BLACK : strPlateColor := '黑色';
      LC_GREEN : strPlateColor := '绿色';
    end;

    with lvQueryResult.Items.Add do
    begin
      Caption := strPlateID;
      SubItems.Add(StrPas(pResult.license));
      SubItems.Add(szTime);
      SubItems.Add(strResultType);
      SubItems.Add(strPlateColor);
    end;
  end;

  FreeMem(pResult);
end;

//获取句柄及初始化
procedure  TFormPlateQuery.SetLPRHandle(mhandle : Integer; picPath:string );
begin
  txtPicPath := picPath;
  m_hLPRClient := mhandle;
  if (m_hLPRClient <> 0) then
  begin
    VzLPRClient_SetQueryPlateCallBack(m_hLPRClient, gOnQueryPlateInfo, Self);
  end;
end;

//创建窗口
procedure TFormPlateQuery.FromPlateQuery_Create(Sender: TObject);
var
   sStartDate, sStartTime, sEndDate, sEndTime : string;
   Size : Byte;
begin
  ONE_PAGE_COUNT := 20;

  lvQueryResult.Columns.Add;
  lvQueryResult.Columns.Add;
  lvQueryResult.Columns.Add;
  lvQueryResult.Columns.Add;
  lvQueryResult.Columns.Add;
  lvQueryResult.Columns[0].Caption := '记录ID';
  lvQueryResult.Columns[1].Caption := '车牌号';
  lvQueryResult.Columns[2].Caption := '记录时间';
  lvQueryResult.Columns[3].Caption := '触发类型';
  lvQueryResult.Columns[4].Caption := '车牌颜色';
  lvQueryResult.Columns[0].Width := 70;
  lvQueryResult.Columns[1].Width := 90;
  lvQueryResult.Columns[2].Width := 150;
  lvQueryResult.Columns[3].Width := 90;
  lvQueryResult.Columns[4].Width := 70;

  //显示当前的日期和时间
  sStartDate :=  FormatDateTime('ddddd',Now);
  dtpstarttimeday.Date := StrToDateTime(sStartDate);

  sStartTime :=  FormatDateTime('tt',Now);
  dtpstarttimehour.DateTime := StrToDateTime(sStartTime);

  sEndDate := FormatDateTime('ddddd',Now);
  dtpendtimeday.Date := StrToDateTime(sEndDate);

  sEndTime :=  FormatDateTime('tt',Now);
  dtpendtimehour.DateTime := StrToDateTime(sEndTime);
end;

//查询
procedure TFormPlateQuery.btnQueryClick(Sender: TObject);
var
  count, pageSize, nPageCount: Integer;
  FSetting : TFormatSettings;
  sStartDT, sEndDT, sPlate: string;
  szPageMsg : string;
begin
  ListViewClear();  //清空列表

  count := 0;
  m_nTotalCount := 0;
  m_nCurPage := 0;
  m_nPageCount := 0;
  m_strStartTime := '';
  m_strEndTime := '';

  m_strPlate := edtPlateQuery.Text;
  FSetting.ShortDateFormat := 'yyyy-MM-dd ';
  sStartDT := DateTimeToStr(dtpstarttimeday.Date,FSetting) + TimeToStr(dtpstarttimehour.Time);
  sEndDT := DateToStr(dtpendtimeday.Date, FSetting) + TimeToStr(dtpendtimehour.Time);

  count := VzLPRClient_QueryCountByTimeAndPlate(m_hLPRClient, PChar(sStartDT), PChar(sEndDT), PChar(m_strPlate)); //根据时间和车牌号查询记录条数
  if (count > 0 ) then
  begin
    pageSize := count div  ONE_PAGE_COUNT;
    if ((count mod ONE_PAGE_COUNT) = 0) then
    begin
      nPageCount := pageSize;
    end
    else
    begin
      nPageCount := pageSize + 1;
    end;
    szPageMsg := '共' + IntToStr(count) + '条记录, 1/' + IntToStr(nPageCount);

    m_nTotalCount := count;
    m_nPageCount := nPageCount;
    m_strStartTime := sStartDT;
    m_strEndTime := sEndDT;

    QueryByPage(0);
  end
  else
  begin
    szPageMsg :=  '共 0 条记录';
  end;
   m_strPageMsg := szPageMsg;

  //显示查询车牌的记录消息
  PlatePageMsgShow();

end;

//显示查询车牌的记录消息
procedure  TFormPlateQuery.PlatePageMsgShow();
begin
  lblPageMsg.Caption := m_strPageMsg;
end;

//清空列表
procedure TFormPlateQuery.ListViewClear();
begin
  lvQueryResult.Items.Clear;
end;

//分页查询
procedure TFormPlateQuery.QueryByPage(nPageIndex : Integer);
var
  mRet : Integer;
begin
  ListViewClear();

  //根据时间和车牌号查询分页记录
  mRet := VzLPRClient_QueryPageRecordByTimeAndPlate(m_hLPRClient, PChar(m_strStartTime), PChar(m_strEndTime),  PChar(m_strPlate), nPageIndex*ONE_PAGE_COUNT ,(nPageIndex + 1)*ONE_PAGE_COUNT);
end;

//上一页
procedure TFormPlateQuery.btnprepageClick(Sender: TObject);
var
  nCurPage : Integer;
  sPageMsg : string;
begin
  nCurPage := m_nCurPage -1;
  if (nCurPage >= 0) then
  begin
    QueryByPage(nCurPage);  //查询上一页记录

    Dec(m_nCurPage);

    m_strPageMsg := '共' + IntToStr(m_nTotalCount) + '条记录, ' + IntToStr(nCurPage + 1) + '/' + IntToStr(m_nPageCount);
    PlatePageMsgShow();
  end
  else
  begin
    ShowMessage('无上一页记录!');
  end;

end;

//下一页
procedure TFormPlateQuery.btnnextpageClick(Sender: TObject);
var
  nCurPage : Integer;
begin
  nCurPage := m_nCurPage + 1;
  if ((nCurPage >0) and (nCurPage < m_nPageCount)) then
  begin
    QueryByPage(nCurPage);

    Inc(m_nCurPage);

    m_strPageMsg := '共' + IntToStr(m_nTotalCount) + '条记录, '  + IntToStr(nCurPage + 1) + '/' + IntToStr(m_nPageCount);
    PlatePageMsgShow();
  end
  else
  begin
    ShowMessage('无下一页记录!');
  end;
end;

//列表双击时间
procedure TFormPlateQuery.QueryResult_DbClick(Sender: TObject);
var
  id, mRet : Integer;
  filePath,imgPath : string;
  FileStream : TFileStream;
  formPlateShow : TFormPlateShow;
begin
  if(Assigned(lvQueryResult.Selected)) then  //判断双击区域是否有效
  begin
    id := StrToInt(lvQueryResult.Selected.Caption);

    m_uSizeBufJpg :=  1024 * 1024;
    GetMem(m_pBufJeg, m_uSizeBufJpg) ;
    ZeroMemory(m_pBufJeg, m_uSizeBufJpg);

    mRet := VzLPRClient_LoadImageById(m_hLPRClient, id, m_pBufJeg, @m_uSizeBufJpg);//根据ID获取车牌图片
    if(mRet <> 0) then
    begin
      Exit;
    end;

    filePath := txtPicPath +'Query\';
    if not DirectoryExists(filePath) then
    begin
       ForceDirectories(filePath);//创建目录
    end;

    imgPath := filePath + IntToStr(id) + '.jpg';
    if ((mRet = 0)and (m_uSizeBufJpg > 0) and (m_uSizeBufJpg < 1024 * 1024)) then
    begin
      FileStream := TFileStream.Create(imgPath, fmCreate);
      FileStream.Position := 0;
      FileStream.WriteBuffer(m_pBufJeg^,m_uSizeBufJpg);
      FreeAndNil(FileStream);
    end
    else
    begin
      ShowMessage('图片已删除!');
      Exit;
    end;

    formPlateShow := TFormPlateShow.Create(Application);
    formPlateShow.Show;
    formPlateShow.SetPicPath(imgPath);
  end;

  FreeMem(m_pBufJeg);

end;

end.
