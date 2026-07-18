program VzDelphiSDKDemo;

uses
  Forms,
  VzDephiSDKDemoMain in 'VzDephiSDKDemoMain.pas' {FormMain},
  frmOSDSet in 'frmOSDSet.pas' {FormOSDSet},
  frmBaseCfg in 'frmBaseCfg.pas' {FormBaseCfg},
  frmVideoCfg in 'frmVideoCfg.pas' {FormVideoCfg},
  frmInAndOut in 'frmInAndOut.pas' {FormInAndOut},
  frmWhiteList in 'frmWhiteList.pas' {FormWhiteList},
  frmPlateQuery in 'frmPlateQuery.pas' {FormPlateQuery},
  frmSerialControl in 'frmSerialControl.pas' {FormSerialControl},
  frmWLAdd in 'frmWLAdd.pas' {FormWLAdd},
  frmWLEdit in 'frmWLEdit.pas' {FormWLEdit},
  VzLPRSDK in 'VzLPRSDK.pas',
  frmNetCfg in 'frmNetCfg.pas' {FormNetCfg},
  frmPlateShow in 'frmPlateShow.pas' {FormPlateShow},
  frmPlayVoice in 'frmPlayVoice.pas' {FormPlayVoice};

{$R *.res}

begin
  Application.Initialize;
  Application.CreateForm(TFormMain, FormMain);
  Application.CreateForm(TFormOSDSet, FormOSDSet);
  Application.CreateForm(TFormBaseCfg, FormBaseCfg);
  Application.CreateForm(TFormVideoCfg, FormVideoCfg);
  Application.CreateForm(TFormInAndOut, FormInAndOut);
  Application.CreateForm(TFormWhiteList, FormWhiteList);
  Application.CreateForm(TFormPlateQuery, FormPlateQuery);
  Application.CreateForm(TFormSerialControl, FormSerialControl);
  Application.CreateForm(TFormWLAdd, FormWLAdd);
  Application.CreateForm(TFormWLEdit, FormWLEdit);
  Application.CreateForm(TFormNetCfg, FormNetCfg);
  Application.CreateForm(TFormPlateShow, FormPlateShow);
  Application.CreateForm(TFormPlayVoice, FormPlayVoice);
  Application.Run;
end.
