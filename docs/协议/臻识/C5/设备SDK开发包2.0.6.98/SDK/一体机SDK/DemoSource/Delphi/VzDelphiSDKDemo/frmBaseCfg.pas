unit frmBaseCfg;

interface

uses
  Windows, Messages, SysUtils, Variants, Classes, Graphics, Controls, Forms,
  Dialogs, StdCtrls, ExtCtrls, VzLPRSDK;

type
  TFormBaseCfg = class(TForm)
    lbl1: TLabel;
    chkRealResult: TCheckBox;
    chkPlatePoint: TCheckBox;
    chkVirtualAndReco: TCheckBox;
    chkStableTri: TCheckBox;
    lbl2: TLabel;
    chkVirtualTri: TCheckBox;
    chkIO1Tri: TCheckBox;
    chkIO2Tri: TCheckBox;
    chkIO3Tri: TCheckBox;
    lbl3: TLabel;
    chkBlue: TCheckBox;
    chkYellow: TCheckBox;
    chkBlack: TCheckBox;
    chkCoach: TCheckBox;
    chkPolice: TCheckBox;
    chkArm: TCheckBox;
    chkTag: TCheckBox;
    chkHK: TCheckBox;
    chkEC: TCheckBox;
    btnOK: TButton;
    procedure SetLPRHandle(handle: Integer);
    procedure btnOKClick(Sender: TObject);
  private
    procedure GetRealTimeResult;
    function SetRealTimeResult: Boolean;
    procedure GetTrigType;
    function SetTrigType: Boolean;
    procedure getPlateRecType;
    function setPlateRecType: Boolean;
    { Private declarations }
  public
    { Public declarations }
  end;

var
  FormBaseCfg: TFormBaseCfg;
  m_hLPRClient : Integer;
  drawMode : VZ_LPRC_DRAWMODE;
  uBitsRecType : Integer;
  uBitsTrigType : Integer;

implementation

{$R *.dfm}
procedure  TFormBaseCfg.SetLPRHandle(handle: Integer);
begin
  m_hLPRClient := handle;

  //获取实时显示
  GetRealTimeResult();

  //获取输出结果
  GetTrigType();

  //获取识别车牌类型
  getPlateRecType();

end;

//获取实时显示
procedure  TFormBaseCfg.GetRealTimeResult();
var
   mRet : Integer;
begin
  mRet := VzLPRClient_GetDrawMode(m_hLPRClient, @drawMode); //获取智能视频显示模式

  chkRealResult.Checked := (drawMode.byDspAddTarget = 1);//实时结果
  chkPlatePoint.Checked := (drawMode.byDspAddTrajectory = 1);//车牌位置
  chkVirtualAndReco.Checked := (drawMode.byDspAddRule = 1) //虚拟线圈和识别区域
end;

//设置实时显示
function TFormBaseCfg.SetRealTimeResult():Boolean;
var
   drawMode : VZ_LPRC_DRAWMODE;
   mRet : Integer;
   bFuncRet : Boolean;
begin
   bFuncRet := True;
   drawMode.byDspAddTarget := Integer(chkRealResult.Checked);
   drawMode.byDspAddTrajectory := Integer(chkPlatePoint.Checked);
   drawMode.byDspAddRule := Integer(chkVirtualAndReco.Checked);

   mRet := VzLPRClient_SetDrawMode(m_hLPRClient, @drawMode); //设置智能视频显示模式
   if (mRet <> 0) then
   begin
     ShowMessage('设置实时显示失败! ');
     bFuncRet := False;
   end;
   Result := bFuncRet;
end;

//获取输出结果
procedure  TFormBaseCfg.GetTrigType();
var
  uBitsTrigType : Integer;
  mRet : Integer;
begin
  mRet := VzLPRClient_GetPlateTrigType(m_hLPRClient, uBitsTrigType);  //获取已设置的允许的车牌识别触发类型

  chkStableTri.Checked := ((uBitsTrigType and VZ_LPRC_TRIG_ENABLE_STABLE ) <> 0); //稳定识别触发
  chkVirtualTri.Checked := ((uBitsTrigType and VZ_LPRC_TRIG_ENABLE_VLOOP ) <> 0); //虚拟线圈触发
  chkIO1Tri.Checked := ((uBitsTrigType and VZ_LPRC_TRIG_ENABLE_IO_IN1 ) <> 0);    //IO输入1触发
  chkIO2Tri.Checked := ((uBitsTrigType and VZ_LPRC_TRIG_ENABLE_IO_IN2 ) <> 0);    //IO输入2触发
  chkIO3Tri.Checked := ((uBitsTrigType and VZ_LPRC_TRIG_ENABLE_IO_IN3 ) <> 0);    //IO输入3触发
end;

//设置触发类型
function  TFormBaseCfg.SetTrigType():Boolean;
var
  mRet : Integer;
  bFuncRet : Boolean;
begin
  bFuncRet := True;

  //稳定识别触发
  if (chkStableTri.Checked) then
  begin
    uBitsTrigType := uBitsTrigType or VZ_LPRC_TRIG_ENABLE_STABLE;
  end
  else
  begin
    uBitsTrigType := uBitsTrigType or 0;
  end;

  //虚拟线圈触发
  if (chkVirtualTri.Checked) then
  begin
    uBitsTrigType := uBitsTrigType or VZ_LPRC_TRIG_ENABLE_VLOOP;
  end
  else
  begin
    uBitsTrigType := uBitsTrigType or 0;
  end;

  //IO输入1触发
  if(chkIO1Tri.Checked) then
  begin
    uBitsTrigType := uBitsTrigType or VZ_LPRC_TRIG_ENABLE_IO_IN1;
  end
  else
  begin
    uBitsTrigType := uBitsTrigType or 0;
  end;

  //IO输入2触发
  if(chkIO2Tri.Checked) then
  begin
    uBitsTrigType := uBitsTrigType or VZ_LPRC_TRIG_ENABLE_IO_IN2;
  end
  else
  begin
    uBitsTrigType := uBitsTrigType or 0;
  end;

  //IO输入3触发
  if(chkIO3Tri.Checked) then
  begin
    uBitsTrigType := uBitsTrigType or VZ_LPRC_TRIG_ENABLE_IO_IN3;
  end
  else
  begin
    uBitsTrigType := uBitsTrigType or 0;
  end;

  mRet := VzLPRClient_SetPlateTrigType(m_hLPRClient, uBitsTrigType);
  if (mRet <> 0) then
  begin
    ShowMessage('设置输出结果失败!');
    bFuncRet := False;
  end;

  Result := bFuncRet;
end;

//获取识别车牌类型
procedure TFormBaseCfg.getPlateRecType();
var
  uBitsRecType :Integer;
  mRet : Integer;
begin
  mRet := VzLPRClient_GetPlateRecType(m_hLPRClient, uBitsRecType); //获取已设置的需要识别的车牌类型

  chkBlue.Checked := ((uBitsRecType and VZ_LPRC_REC_BLUE) <> 0 );
  chkYellow.Checked := ((uBitsRecType and VZ_LPRC_REC_YELLOW) <> 0 );
  chkBlack.Checked := ((uBitsRecType and VZ_LPRC_REC_BLACK) <> 0 );
  chkCoach.Checked := ((uBitsRecType and VZ_LPRC_REC_COACH) <> 0 );
  chkPolice.Checked := ((uBitsRecType and VZ_LPRC_REC_POLICE) <> 0 );
  chkArm.Checked :=  ((uBitsRecType and VZ_LPRC_REC_AMPOL) <> 0 );
  chkTag.Checked :=  ((uBitsRecType and VZ_LPRC_REC_ARMY) <> 0 );
  chkHK.Checked :=  ((uBitsRecType and VZ_LPRC_REC_GANGAO) <> 0 );
  chkEC.Checked :=  ((uBitsRecType and VZ_LPRC_REC_EMBASSY) <> 0 );
end;

//设置车牌类型
function  TFormBaseCfg.setPlateRecType():Boolean;
var
  mRet : Integer;
  bFuncRet : Boolean;
begin
  bFuncRet := True;

  //蓝牌
  if (chkBlue.Checked) then
  begin
    uBitsRecType := uBitsRecType or VZ_LPRC_REC_BLUE;
  end
  else
  begin
    uBitsRecType := uBitsRecType or 0;
  end;

  //黄牌
  if (chkYellow.Checked) then
  begin
    uBitsRecType := uBitsRecType or VZ_LPRC_REC_YELLOW;
  end
  else
  begin
    uBitsRecType := uBitsRecType or 0;
  end;

  //黑牌
  if(chkBlack.Checked) then
  begin
    uBitsRecType := uBitsRecType or VZ_LPRC_REC_BLACK;
  end
  else
  begin
    uBitsRecType := uBitsRecType or 0;
  end;

  //教练
  if(chkCoach.Checked) then
  begin
    uBitsRecType := uBitsRecType or VZ_LPRC_REC_COACH;
  end
  else
  begin
    uBitsRecType := uBitsRecType or 0;
  end;

  //警牌
  if(chkPolice.Checked) then
  begin
    uBitsRecType := uBitsRecType or VZ_LPRC_REC_POLICE;
  end
  else
  begin
    uBitsRecType := uBitsRecType or 0;
  end;

  //武警
  if(chkArm.Checked) then
  begin
    uBitsRecType := uBitsRecType or VZ_LPRC_REC_AMPOL;
  end
  else
  begin
    uBitsRecType := uBitsRecType or 0;
  end;

  //军牌
  if(chkTag.Checked) then
  begin
    uBitsRecType := uBitsRecType or VZ_LPRC_REC_ARMY;
  end
  else
  begin
    uBitsRecType := uBitsRecType or 0;
  end;

  //港澳
  if(chkHK.Checked) then
  begin
    uBitsRecType := uBitsRecType or VZ_LPRC_REC_GANGAO;
  end
  else
  begin
    uBitsRecType := uBitsRecType or 0;
  end;

  //领事馆
  if(chkEC.Checked) then
  begin
    uBitsRecType := uBitsRecType or VZ_LPRC_REC_EMBASSY;
  end
  else
  begin
    uBitsRecType := uBitsRecType or 0;
  end;

  mRet := VzLPRClient_SetPlateRecType(m_hLPRClient, uBitsRecType);//设置需要识别的车牌类型
  if(mRet <> 0) then
  begin
    ShowMessage('设置车牌类型失败!');
    bFuncRet := False;
  end;

  Result := bFuncRet;
end;

//确定
procedure TFormBaseCfg.btnOKClick(Sender: TObject);
var
  bRecRet,bTrigRet,bRealRet:Boolean ;
begin
  bRealRet := SetRealTimeResult();
  bTrigRet := SetTrigType();
  bRecRet := setPlateRecType();
  if (bRealRet and bTrigRet and bRecRet) then
  begin
    ShowMessage('基本配置设置成功! ');
  end;

  Self.Close;
end;

end.
