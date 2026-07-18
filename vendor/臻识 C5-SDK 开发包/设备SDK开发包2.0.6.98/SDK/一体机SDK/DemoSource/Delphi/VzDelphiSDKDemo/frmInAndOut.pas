unit frmInAndOut;

interface

uses
  Windows, Messages, SysUtils, Variants, Classes, Graphics, Controls, Forms,
  Dialogs, ExtCtrls, VzLPRSDK, StdCtrls, ComCtrls;

type
  TFormInAndOut = class(TForm)
    pgcInAndOut: TPageControl;
    tsOutputCfg: TTabSheet;
    tsTriggerDelay: TTabSheet;
    tsWhitelistCfg: TTabSheet;
    tsSerialParam: TTabSheet;
    lbl1: TLabel;
    edtTriggerDelay: TEdit;
    lbl2: TLabel;
    lbl3: TLabel;
    grp1: TGroupBox;
    lbl4: TLabel;
    rbOffineAuto: TRadioButton;
    rbWhiteStart: TRadioButton;
    rbWhiteStop: TRadioButton;
    btnWhiteVerify: TButton;
    grp2: TGroupBox;
    lbl5: TLabel;
    rbWhiteAllMatch: TRadioButton;
    rbWhitePartMatch: TRadioButton;
    rbWhiteFuccyMatch: TRadioButton;
    lbl6: TLabel;
    pnl1: TPanel;
    rbWhiteLen1: TRadioButton;
    rbWhiteLen2: TRadioButton;
    rbWhiteLen3: TRadioButton;
    chkIgnore: TCheckBox;
    btnWhiteMatch: TButton;
    lbl7: TLabel;
    lbl8: TLabel;
    lbl9: TLabel;
    lbl10: TLabel;
    lbl11: TLabel;
    cbbSerialPort: TComboBox;
    cbbBound: TComboBox;
    cbbParity: TComboBox;
    cbbStop: TComboBox;
    cbbDataBit: TComboBox;
    btnSerialParam: TButton;
    lbl12: TLabel;
    lbl13: TLabel;
    lbl14: TLabel;
    lbl15: TLabel;
    lbl16: TLabel;
    lbl17: TLabel;
    lbl18: TLabel;
    lbl19: TLabel;
    lbl20: TLabel;
    lbl21: TLabel;
    lbl22: TLabel;
    lbl23: TLabel;
    lbl24: TLabel;
    lbl25: TLabel;
    lbl26: TLabel;
    chk1: TCheckBox;
    chk2: TCheckBox;
    chk3: TCheckBox;
    chk4: TCheckBox;
    chk5: TCheckBox;
    chk6: TCheckBox;
    chk7: TCheckBox;
    chk8: TCheckBox;
    chk9: TCheckBox;
    chk10: TCheckBox;
    chk11: TCheckBox;
    chk12: TCheckBox;
    chk13: TCheckBox;
    chk14: TCheckBox;
    chk15: TCheckBox;
    chk16: TCheckBox;
    chk17: TCheckBox;
    chk18: TCheckBox;
    chk19: TCheckBox;
    chk20: TCheckBox;
    chk21: TCheckBox;
    chk22: TCheckBox;
    chk23: TCheckBox;
    chk24: TCheckBox;
    chk25: TCheckBox;
    chk26: TCheckBox;
    chk27: TCheckBox;
    chk28: TCheckBox;
    chk29: TCheckBox;
    chk30: TCheckBox;
    chk31: TCheckBox;
    chk32: TCheckBox;
    chk33: TCheckBox;
    chk34: TCheckBox;
    chk35: TCheckBox;
    chk36: TCheckBox;
    chk37: TCheckBox;
    chk38: TCheckBox;
    chk39: TCheckBox;
    chk40: TCheckBox;
    chk41: TCheckBox;
    chk42: TCheckBox;
    chk43: TCheckBox;
    chk44: TCheckBox;
    chk45: TCheckBox;
    chk46: TCheckBox;
    chk47: TCheckBox;
    chk48: TCheckBox;
    chk49: TCheckBox;
    chk50: TCheckBox;
    chk51: TCheckBox;
    chk52: TCheckBox;
    chk53: TCheckBox;
    chk54: TCheckBox;
    chk55: TCheckBox;
    chk56: TCheckBox;
    btnTriggerDelay: TButton;
    btnOutpputCfg: TButton;
    procedure SetLPRHandle(mhandle: Integer);
    procedure btnTriggerDelayClick(Sender: TObject);
    procedure btnWhiteVerifyClick(Sender: TObject);
    procedure btnWhiteMatchClick(Sender: TObject);
    procedure rbWhiteAllMatch_Clik(Sender: TObject);
    procedure rbWhitePartMatch_Clik(Sender: TObject);
    procedure rbWhiteFuccyMatch_Clik(Sender: TObject);
    procedure cbbSerialPort_Change(Sender: TObject);
    procedure btnSerialParamClick(Sender: TObject);
    procedure chk1Click(Sender: TObject);
    procedure btnOutpputCfgClick(Sender: TObject);
    procedure chk2Click(Sender: TObject);
    procedure chk3Click(Sender: TObject);
    procedure chk4Click(Sender: TObject);
    procedure chk5Click(Sender: TObject);
    procedure chk6Click(Sender: TObject);
    procedure chk7Click(Sender: TObject);
    procedure chk8Click(Sender: TObject);
    procedure chk16Click(Sender: TObject);
    procedure chk15Click(Sender: TObject);
    procedure chk14Click(Sender: TObject);
    procedure chk13Click(Sender: TObject);
    procedure chk12Click(Sender: TObject);
    procedure chk11Click(Sender: TObject);
    procedure chk10Click(Sender: TObject);
    procedure chk9Click(Sender: TObject);
    procedure chk24Click(Sender: TObject);
    procedure chk23Click(Sender: TObject);
    procedure chk22Click(Sender: TObject);
    procedure chk21Click(Sender: TObject);
    procedure chk20Click(Sender: TObject);
    procedure chk19Click(Sender: TObject);
    procedure chk18Click(Sender: TObject);
    procedure chk17Click(Sender: TObject);
    procedure chk32Click(Sender: TObject);
    procedure chk31Click(Sender: TObject);
    procedure chk30Click(Sender: TObject);
    procedure chk29Click(Sender: TObject);
    procedure chk28Click(Sender: TObject);
    procedure chk27Click(Sender: TObject);
    procedure chk26Click(Sender: TObject);
    procedure chk25Click(Sender: TObject);
    procedure chk40Click(Sender: TObject);
    procedure chk39Click(Sender: TObject);
    procedure chk38Click(Sender: TObject);
    procedure chk37Click(Sender: TObject);
    procedure chk36Click(Sender: TObject);
    procedure chk35Click(Sender: TObject);
    procedure chk48Click(Sender: TObject);
    procedure chk47Click(Sender: TObject);
    procedure chk46Click(Sender: TObject);
    procedure chk45Click(Sender: TObject);
    procedure chk44Click(Sender: TObject);
    procedure chk43Click(Sender: TObject);
    procedure chk56Click(Sender: TObject);
    procedure chk55Click(Sender: TObject);
    procedure chk54Click(Sender: TObject);
    procedure chk53Click(Sender: TObject);
    procedure chk52Click(Sender: TObject);
    procedure chk51Click(Sender: TObject);
  private
    procedure initDelay;
    procedure initWLCheck;
    procedure initWLMatch;
    procedure initSerialParam;
    procedure LoadSerialParam(nPort: Integer);
    procedure initOutput;
    function BooleanToInt(Sender: TObject): Integer;
    { Private declarations }
  public
    { Public declarations }
  end;

var
  FormInAndOut: TFormInAndOut;
  m_hLPRClient : Integer;
  m_oOutputInfo : VZ_OutputConfigInfo;

implementation

{$R *.dfm}
//设置打开设备句柄
procedure   TFormInAndOut.SetLPRHandle(mhandle : Integer);
begin
  m_hLPRClient := mhandle;

  //初始化输出配置
  initOutput();

  //初始化车牌触发延迟时间
  initDelay();

  //初始化白名单启用条件
  initWLCheck();

  //初始化白名单模糊匹配条件
  initWLMatch();

  //初始化串口参数
  initSerialParam();

end;

//初始化输出配置
procedure TFormInAndOut.initOutput();
var
  mRet : Integer;
begin
  mRet := VzLPRClient_GetOutputConfig(m_hLPRClient, @m_oOutputInfo);//获取输出配置

  //识别通过
  chk1.Checked := (m_oOutputInfo.oConfigInfo[0].switchout1 = 1);
  chk2.Checked := (m_oOutputInfo.oConfigInfo[0].switchout2 = 1);
  chk3.Checked := (m_oOutputInfo.oConfigInfo[0].switchout3 = 1);
  chk4.Checked := (m_oOutputInfo.oConfigInfo[0].switchout4 = 1);
  chk5.Checked := (m_oOutputInfo.oConfigInfo[0].levelout1 = 1);
  chk6.Checked := (m_oOutputInfo.oConfigInfo[0].levelout2 = 1);
  chk7.Checked := (m_oOutputInfo.oConfigInfo[0].rs485out1 = 1);
  chk8.Checked := (m_oOutputInfo.oConfigInfo[0].rs485out2 = 1);

  //识别不通过
  chk16.Checked := (m_oOutputInfo.oConfigInfo[1].switchout1 = 1);
  chk15.Checked := (m_oOutputInfo.oConfigInfo[1].switchout2 = 1);
  chk14.Checked := (m_oOutputInfo.oConfigInfo[1].switchout3 = 1);
  chk13.Checked := (m_oOutputInfo.oConfigInfo[1].switchout4 = 1);
  chk12.Checked := (m_oOutputInfo.oConfigInfo[1].levelout1 = 1);
  chk11.Checked := (m_oOutputInfo.oConfigInfo[1].levelout2 = 1);
  chk10.Checked := (m_oOutputInfo.oConfigInfo[1].rs485out1 = 1);
  chk9.Checked  := (m_oOutputInfo.oConfigInfo[1].rs485out2 = 1);

  //无车牌
  chk24.Checked := (m_oOutputInfo.oConfigInfo[2].switchout1 = 1);
  chk23.Checked := (m_oOutputInfo.oConfigInfo[2].switchout2 = 1);
  chk22.Checked := (m_oOutputInfo.oConfigInfo[2].switchout3 = 1);
  chk21.Checked := (m_oOutputInfo.oConfigInfo[2].switchout4 = 1);
  chk20.Checked := (m_oOutputInfo.oConfigInfo[2].levelout1 = 1);
  chk19.Checked := (m_oOutputInfo.oConfigInfo[2].levelout2 = 1);
  chk18.Checked := (m_oOutputInfo.oConfigInfo[2].rs485out1 = 1);
  chk17.Checked := (m_oOutputInfo.oConfigInfo[2].rs485out2 = 1);

  //黑名单
  chk32.Checked := (m_oOutputInfo.oConfigInfo[3].switchout1 = 1);
  chk31.Checked := (m_oOutputInfo.oConfigInfo[3].switchout2 = 1);
  chk30.Checked := (m_oOutputInfo.oConfigInfo[3].switchout3 = 1);
  chk29.Checked := (m_oOutputInfo.oConfigInfo[3].switchout4 = 1);
  chk28.Checked := (m_oOutputInfo.oConfigInfo[3].levelout1 = 1);
  chk27.Checked := (m_oOutputInfo.oConfigInfo[3].levelout2 = 1);
  chk26.Checked := (m_oOutputInfo.oConfigInfo[3].rs485out1 = 1);
  chk25.Checked := (m_oOutputInfo.oConfigInfo[3].rs485out2 = 1);

  //开关量/电平输入1
  chk40.Checked := (m_oOutputInfo.oConfigInfo[4].switchout1 = 1);
  chk39.Checked := (m_oOutputInfo.oConfigInfo[4].switchout2 = 1);
  chk38.Checked := (m_oOutputInfo.oConfigInfo[4].switchout3 = 1);
  chk37.Checked := (m_oOutputInfo.oConfigInfo[4].switchout4 = 1);
  chk36.Checked := (m_oOutputInfo.oConfigInfo[4].levelout1 = 1);
  chk35.Checked := (m_oOutputInfo.oConfigInfo[4].levelout2 = 1);

  //开关量/电平输入2
  chk48.Checked := (m_oOutputInfo.oConfigInfo[5].switchout1 = 1);
  chk47.Checked := (m_oOutputInfo.oConfigInfo[5].switchout2 = 1);
  chk46.Checked := (m_oOutputInfo.oConfigInfo[5].switchout3 = 1);
  chk45.Checked := (m_oOutputInfo.oConfigInfo[5].switchout4 = 1);
  chk44.Checked := (m_oOutputInfo.oConfigInfo[5].levelout1 = 1);
  chk43.Checked := (m_oOutputInfo.oConfigInfo[5].levelout2 = 1);

  //开关量/电平输入3
  chk56.Checked := (m_oOutputInfo.oConfigInfo[6].switchout1 = 1);
  chk55.Checked := (m_oOutputInfo.oConfigInfo[6].switchout2 = 1);
  chk54.Checked := (m_oOutputInfo.oConfigInfo[6].switchout3 = 1);
  chk53.Checked := (m_oOutputInfo.oConfigInfo[6].switchout4 = 1);
  chk52.Checked := (m_oOutputInfo.oConfigInfo[6].levelout1 = 1);
  chk51.Checked := (m_oOutputInfo.oConfigInfo[6].levelout2 = 1);
  
end;

//初始化白名单启用条件
procedure TFormInAndOut.initWLCheck();
var
  nType, mRet : Integer;
begin
  nType := 0;

  mRet := VzLPRClient_GetWLCheckMethod(m_hLPRClient, nType); //获取白名单验证模式
  if (mRet = 0) then
  begin
    if (nType = 0) then
    begin
       rbOffineAuto.Checked := True;  //脱机自动启用
    end
    else if (nType = 1) then
    begin
      rbWhiteStart.Checked := True;  //启用
    end
    else if (nType = 2) then
    begin
      rbWhiteStop.Checked := True;  //不启用
    end;
  end;
end;

//初始化白名单模糊匹配条件
procedure TFormInAndOut.initWLMatch();
var
  nType, nLen, mRet : Integer;
  bIgnore : Boolean;
begin
  nType := 0;
  nLen  := 0;
  bIgnore := False;

  mRet := VzLPRClient_GetWLFuzzy(m_hLPRClient, nType, nLen, bIgnore); //获取白名单模糊匹配
  if (mRet = 0) then
  begin
    if (nType = 0) then
    begin
      rbWhiteAllMatch.Checked := True; //精确匹配
    end
    else if (nType = 1) then
    begin
      rbWhitePartMatch.Checked := True; //相似字符匹配
    end
    else if (nType = 2) then
    begin
      rbWhiteFuccyMatch.Checked := True; //普通字符模糊匹配
    end;

    if(nLen = 1)  then
    begin
      rbWhiteLen1.Checked := True;   //允许误识别长度1
    end
    else if(nLen = 2) then
    begin
      rbWhiteLen2.Checked := True;  //允许误识别长度2
    end
    else if(nLen = 3) then
    begin
      rbWhiteLen3.Checked := True;  //允许误识别长度3
    end;

    chkIgnore.Checked := True;  //忽略汉字
  end;
end;

//初始化串口参数
procedure TFormInAndOut.initSerialParam();

begin
  cbbSerialPort.Items.Add('1');
  cbbSerialPort.Items.Add('2');

  cbbBound.Items.Add('2400');
  cbbBound.Items.Add('4800');
  cbbBound.Items.Add('9600');
  cbbBound.Items.Add('19200');
  cbbBound.Items.Add('38400');
  cbbBound.Items.Add('57600');
  cbbBound.Items.Add('115200');

  cbbParity.Items.Add('无校验');
  cbbParity.Items.Add('奇校验');
  cbbParity.Items.Add('偶校验');

  cbbStop.Items.Add('1');
  cbbStop.Items.Add('2');

  cbbDataBit.Items.Add('8');

  //获取串口参数
  LoadSerialParam(0);
   
end;

//获取串口参数
procedure TFormInAndOut.LoadSerialParam(nPort : Integer);
var
  oSerialParam : VZ_SERIAL_PARAMETER;
  mRet : Integer;
begin
  cbbSerialPort.ItemIndex := nPort;

  mRet := VzLPRClient_GetSerialParameter(m_hLPRClient, nPort, @oSerialParam);
  if (mRet = 0) then
  begin
    //串口号
    cbbBound.ItemIndex := nPort;

    //波特率
    case  oSerialParam.uBaudRate of
     2400   : cbbBound.ItemIndex := 0;
     4800   : cbbBound.ItemIndex := 1;
     9600   : cbbBound.ItemIndex := 2;
     19200  : cbbBound.ItemIndex := 3;
     38400  : cbbBound.ItemIndex := 4;
     57600  : cbbBound.ItemIndex := 5;
     115200 : cbbBound.ItemIndex := 6;
    end ;

    //校验位
    cbbParity.ItemIndex := oSerialParam.uParity;

    //停止位
    cbbStop.ItemIndex := oSerialParam.uStopBit - 1;

    //数据位
    if (oSerialParam.uDataBits = 8) then
    begin
      cbbDataBit.ItemIndex := 0;
    end;
  end;
end;

//初始化车牌触发延迟时间
procedure TFormInAndOut.initDelay();
var
  nDelay , mRet : Integer;
begin
  nDelay := 0;

  mRet := VzLPRClient_GetTriggerDelay(m_hLPRClient, nDelay); //获取车牌识别触发延迟时间
  if (mRet = 0) then
  begin
    edtTriggerDelay.Text := IntToStr(nDelay);
  end;
end;

//设置车牌触发延迟时间
procedure TFormInAndOut.btnTriggerDelayClick(Sender: TObject);
var
  nDelay , mRet : Integer;
begin
  nDelay := StrToInt(edtTriggerDelay.Text);

  mRet := VzLPRClient_SetTriggerDelay(m_hLPRClient, nDelay);
  if (mRet = 0) then
  begin
    ShowMessage('设置触发延迟时间成功 !');
  end
  else
  begin
    ShowMessage('设置触发延迟时间失败 !');
  end;
end;

//设置白名单启用条件
procedure TFormInAndOut.btnWhiteVerifyClick(Sender: TObject);
var
  nType ,mRet :Integer;
begin
  nType := 0;
  if(rbOffineAuto.Checked) then
  begin
    nType := 0 ;
  end
  else
  if (rbWhiteStart.Checked) then
  begin
    nType := 1;
  end
  else
  if (rbWhiteStop.Checked) then
  begin
    nType := 2;
  end;

  mRet := VzLPRClient_SetWLCheckMethod(m_hLPRClient, nType); //设置白名单验证模式
  if (mRet <> 0) then
  begin
    ShowMessage('设置白名单启用条件失败 !');
  end
  else
  begin
    ShowMessage('设置白名单启用条件成功 !');
  end;

end;

//设置白名单模糊匹配
procedure TFormInAndOut.btnWhiteMatchClick(Sender: TObject);
var
  nType, nLen, mRet : Integer;
  bIgnore : Boolean;
begin
  nType := 0;
  nLen  := 0;
  bIgnore := False;

  //三种匹配方式 三选一
  if (rbWhiteAllMatch.Checked) then
  begin
    nType := 0;
  end
  else if(rbWhitePartMatch.Checked) then
  begin
    nType := 1;
  end
  else if(rbWhiteFuccyMatch.Checked) then
  begin
    nType := 2;
  end;

  //三种误识别长度 三选一
  if (rbWhiteLen1.Checked) then
  begin
    nLen := 1;
  end
  else if(rbWhiteLen2.Checked) then
  begin
    nLen := 2;
  end
  else if(rbWhiteLen3.Checked) then
  begin
    nLen := 3;
  end;

  //忽略汉字
  if (chkIgnore.Checked) then
  begin
    bIgnore := True;
  end;

  mRet := VzLPRClient_SetWLFuzzy(m_hLPRClient, nType, nLen, bIgnore);//设置白名单模糊匹配
  if (mRet <> 0) then
  begin
     ShowMessage('设置模糊查询方式失败 !');
  end
  else
  begin
    ShowMessage('设置模糊查询方式成功 !');
  end;
end;

//点击"精确匹配“按钮事件
procedure TFormInAndOut.rbWhiteAllMatch_Clik(Sender: TObject);
begin
  rbWhiteLen1.Enabled := False;
  rbWhiteLen2.Enabled := False;
  rbWhiteLen3.Enabled := False;

  rbWhiteLen1.Checked := False;
  rbWhiteLen2.Checked := False;
  rbWhiteLen3.Checked := False;
end;

//点击"相似字符匹配”按钮事件
procedure TFormInAndOut.rbWhitePartMatch_Clik(Sender: TObject);
begin
  rbWhiteLen1.Enabled := False;
  rbWhiteLen2.Enabled := False;
  rbWhiteLen3.Enabled := False;

  rbWhiteLen1.Checked := False;
  rbWhiteLen2.Checked := False;
  rbWhiteLen3.Checked := False;
end;

//点击"普通字符模糊匹配“按钮事件
procedure TFormInAndOut.rbWhiteFuccyMatch_Clik(Sender: TObject);
begin
  rbWhiteLen1.Enabled := True;
  rbWhiteLen2.Enabled := True;
  rbWhiteLen3.Enabled := True;

  rbWhiteLen1.Checked := True;
end;

//串口号Chang事件
procedure TFormInAndOut.cbbSerialPort_Change(Sender: TObject);
var
  nPort : Integer;
begin
  nPort := cbbSerialPort.ItemIndex;
  LoadSerialParam(nPort);
end;

//设置串口参数
procedure TFormInAndOut.btnSerialParamClick(Sender: TObject);
var
  oSerialParam : VZ_SERIAL_PARAMETER;
  nSerialPort, mRet :Integer;
begin
  //串口号
  nSerialPort := cbbSerialPort.ItemIndex;

  //波特率
  case cbbBound.ItemIndex of
   0 : oSerialParam.uBaudRate := 2400;
   1 : oSerialParam.uBaudRate := 4800;
   2 : oSerialParam.uBaudRate := 9600;
   3 : oSerialParam.uBaudRate := 19200;
   4 : oSerialParam.uBaudRate := 38400;
   5 : oSerialParam.uBaudRate := 57600;
   6 : oSerialParam.uBaudRate := 115200;
  end;

  //校验位
  oSerialParam.uParity := cbbParity.ItemIndex;

  //停止位
  oSerialParam.uStopBit := cbbStop.ItemIndex + 1;

  //数据位
  if cbbDataBit.ItemIndex = 0 then
  begin
    oSerialParam.uDataBits := 8;
  end;

  mRet := VzLPRClient_SetSerialParameter(m_hLPRClient, nSerialPort, @oSerialParam);
  if (mRet <> 0) then
  begin
    ShowMessage('设置串口参数失败!');
  end
  else
  begin
    ShowMessage('设置串口参数成功!');
  end;
  
end;

//将Boolean转换为Int类型
function TFormInAndOut.BooleanToInt(Sender: TObject):Integer;
begin
  if (TCheckBox(Sender).Checked) then
  begin
    Result := 1;
  end
  else
  begin
    Result := 0;
  end;
end;

procedure TFormInAndOut.chk1Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[0].switchout1 := BooleanToInt(Sender);
end;

//设置输出配置
procedure TFormInAndOut.btnOutpputCfgClick(Sender: TObject);
var
  mRet : Integer;
begin
  mRet := VzLPRClient_SetOutputConfig(m_hLPRClient, @m_oOutputInfo);
  if(mRet <> 0 ) then
  begin
    ShowMessage('设置输出配置失败!');
  end
  else
  begin
    ShowMessage('设置输出配置成功!');
  end;
end;

procedure TFormInAndOut.chk2Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[0].switchout2 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk3Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[0].switchout3 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk4Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[0].switchout4 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk5Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[0].levelout1 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk6Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[0].levelout2 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk7Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[0].rs485out1 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk8Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[0].rs485out2 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk16Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[1].switchout1 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk15Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[1].switchout2 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk14Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[1].switchout3 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk13Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[1].switchout4 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk12Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[1].levelout1 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk11Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[1].levelout2 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk10Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[1].rs485out1 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk9Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[1].rs485out2 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk24Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[2].switchout1 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk23Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[2].switchout2 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk22Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[2].switchout3 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk21Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[2].switchout4 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk20Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[2].levelout1 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk19Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[2].levelout2 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk18Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[2].rs485out1 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk17Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[2].rs485out2 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk32Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[3].switchout1 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk31Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[3].switchout2 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk30Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[3].switchout3 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk29Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[3].switchout4 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk28Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[3].levelout1 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk27Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[3].levelout2 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk26Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[3].rs485out1 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk25Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[3].rs485out2 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk40Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[4].switchout1 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk39Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[4].switchout2 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk38Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[4].switchout3 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk37Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[4].switchout4 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk36Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[4].levelout1 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk35Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[4].levelout2 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk48Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[5].switchout1 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk47Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[5].switchout2 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk46Click(Sender: TObject);
begin
   m_oOutputInfo.oConfigInfo[5].switchout3 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk45Click(Sender: TObject);
begin
   m_oOutputInfo.oConfigInfo[5].switchout4 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk44Click(Sender: TObject);
begin
   m_oOutputInfo.oConfigInfo[5].levelout1 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk43Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[5].levelout2 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk56Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[6].switchout1 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk55Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[6].switchout2 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk54Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[6].switchout3 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk53Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[6].switchout4 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk52Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[6].levelout1 := BooleanToInt(Sender);
end;

procedure TFormInAndOut.chk51Click(Sender: TObject);
begin
  m_oOutputInfo.oConfigInfo[6].levelout2 := BooleanToInt(Sender);
end;

end.
