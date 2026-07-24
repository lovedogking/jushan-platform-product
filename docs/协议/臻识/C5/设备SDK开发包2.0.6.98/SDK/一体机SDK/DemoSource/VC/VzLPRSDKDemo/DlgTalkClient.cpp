// DlgTalkClient.cpp : 实现文件
//

#include "stdafx.h"
#include "DlgTalkClient.h"
#include "mmsystem.h"

#define ONPHONE_TEXT _T("通话中")
#define CALLING_TEXT _T("呼叫中")

// CDlgTalkClient 对话框


IMPLEMENT_DYNAMIC(CDlgTalkClient, CDialog)

CDlgTalkClient::CDlgTalkClient(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgTalkClient::IDD, pParent),
  m_rDev(NULL),
  m_recordTime(0),
  m_bRecording(false),
  m_ntalkHandle(0)
{
  m_RequestIP.clear();
}

CDlgTalkClient::~CDlgTalkClient()
{
}

void CDlgTalkClient::InitTreeData(vector <DeviceLPR *> *treedata)
{
  m_rDev = treedata;
}

void CDlgTalkClient::DoDataExchange(CDataExchange* pDX)
{
  CDialog::DoDataExchange(pDX);
  DDX_Control(pDX, IDC_TALK_TREE_DEVICE, m_treeTalkDeviceList);
  DDX_Control(pDX, IDC_BTN_RECIVE, m_btnStartTalk);
  DDX_Control(pDX, IDC_BTN_CLOSE, m_btnStopTalk);
  DDX_Control(pDX, IDC_BTN_RECORD, m_btnRecord);
}

HTREEITEM CDlgTalkClient::finditem(HTREEITEM  item, CString strtext)
{
  HTREEITEM  hfind;
  //空树，直接返回NULL

  if (item == NULL)
    return  NULL;

  //遍历查找
  while (item != NULL)
  {
    //当前节点即所需查找节点
    CString str1 = m_treeTalkDeviceList.GetItemText(item);
    int resultstr1 = str1.Find(strtext);
    if (m_treeTalkDeviceList.GetItemText(item).Find(strtext) >= 0)
      return   item;

    //查找当前节点的子节点
    if (m_treeTalkDeviceList.ItemHasChildren(item))
    {
      item = m_treeTalkDeviceList.GetChildItem(item);
      //递归调用查找子节点下节点
      hfind = finditem(item, strtext);
      if (hfind)
      {
        return  hfind;
      }
      else   //子节点中未发现所需节点，继续查找兄弟节点
        item = m_treeTalkDeviceList.GetNextSiblingItem(m_treeTalkDeviceList.GetParentItem(item));
    }
    else{   //若无子节点，继续查找兄弟节点
      item = m_treeTalkDeviceList.GetNextSiblingItem(item);
    }
  }

  return item;
}

static int CALLBACK TreeCompareProc(LPARAM lParam1, LPARAM lParam2, LPARAM lParamSort)
{
  //把该函数换成自己的规则
  VzTreeCtrl* pmyTreeCtrl = (VzTreeCtrl*)lParamSort;
  int item1Weigth = 0, item2Weigth = 0;
  CString strItem1 = pmyTreeCtrl->GetItemText((HTREEITEM)lParam1);
  CString strItem2 = pmyTreeCtrl->GetItemText((HTREEITEM)lParam2);

  //通话中权重+100
  if (-1 != strItem1.Find(ONPHONE_TEXT)){
    item1Weigth += 100;
  }
  if (-1 != strItem2.Find(ONPHONE_TEXT)){
    item2Weigth += 100;
  }

  //呼叫中权重+10
  if (-1 != strItem1.Find(CALLING_TEXT)){
    item1Weigth += 10;
  }
  if (-1 != strItem2.Find(CALLING_TEXT)){
    item2Weigth += 10;
  }

  //字符对比权重+1
  int compareResult = strItem1.Compare(strItem2);
  item1Weigth += compareResult;

  if (item1Weigth < item2Weigth) return 1;
  else if (item1Weigth == item2Weigth) return 0;
  else if (item1Weigth > item2Weigth) return -1;

  return 0;
}


static void CALLBACK ServerRequestTalkCB(VzLPRClientHandle handle, int state, const char* error_msg, void* pUserData){
  CDlgTalkClient *pTC = (CDlgTalkClient *)pUserData;
  pTC->RequestTalk(handle);
}

void CDlgTalkClient::UpdateTree()
{
  TVSORTCB tvs;
  tvs.hParent = m_treeTalkDeviceList.GetRootItem();
  tvs.lpfnCompare = TreeCompareProc;
  tvs.lParam = (LPARAM)&m_treeTalkDeviceList;

  m_treeTalkDeviceList.SortChildrenCB(&tvs);
}

void CDlgTalkClient::AddOnPhtonText(CString &str)
{
  int str1len = str.GetLength();
  for (int i = str1len; i < 20; ++i)
  {
    str.Append("  ");
  }
  str.Append(ONPHONE_TEXT);
}

void CDlgTalkClient::AddCallingText(CString &str)
{
  int str1len = str.GetLength();
  for (int i = str1len; i < 20; ++i)
  {
    str.Append("  ");
  }
  str.Append(CALLING_TEXT);
}

void CDlgTalkClient::RemoveOnPhoneText(CString &str)
{
  str.Replace(ONPHONE_TEXT, _T(""));
  str.Remove(' ');
}

void CDlgTalkClient::RemoveCallingText(CString &str)
{
  str.Replace(CALLING_TEXT, _T(""));
  str.Remove(' ');
}

//int CDlgTalkClient::GetTalkServerStatus(CString strIP)
//{
//  int status = 0;
//  //VzLPRClient_GetTalkServerStatus(strIP, &status);
//  return status;
//}

int CDlgTalkClient::StartTalkByIP(CString strIP)
{
  HTREEITEM item = finditem(m_treeTalkDeviceList.GetRootItem(), strIP);
  return StartTalkByItem(item);
}

int CDlgTalkClient::StartTalkByItem(HTREEITEM item)
{
  //获取ip地址
  CString strIP = m_treeTalkDeviceList.GetItemText(item);
  RemoveOnPhoneText(strIP);
  RemoveCallingText(strIP);
  int treesize = m_rDev->size();
  m_ntalkHandle = m_treeTalkDeviceList.GetItemData(item);
  char IP[20];
  VzLPRClient_GetDeviceIP(m_ntalkHandle, IP, 20);
  int ret = VzLPRClient_StartTalk(m_ntalkHandle, 640);
  if (-2 == ret)
  {
    //播放忙音
    m_ntalkHandle = 0;
    return ret;
  }
  else if( ret != 0)
  {
	  return ret;
  }

  CString str = m_treeTalkDeviceList.GetItemText(item);
  //清除“通话中”，“呼叫中”字符
  RemoveOnPhoneText(str);
  RemoveCallingText(str);
  //增加“通话中”提示
  AddOnPhtonText(str);
  m_treeTalkDeviceList.SetItemText(item, str);
  UpdateTree();
  return ret;
  //todo
}

void CDlgTalkClient::StopTalkByIP(CString strIP)
{
  HTREEITEM item = finditem(m_treeTalkDeviceList.GetRootItem(), strIP);
  StopTalkByItem(item);
}

void CDlgTalkClient::StopTalkByItem(HTREEITEM item)
{
  CString str = m_treeTalkDeviceList.GetItemText(item);
  //清除“通话中”，“呼叫中”字符
  RemoveOnPhoneText(str);
  RemoveCallingText(str);
  if (m_ntalkHandle)
  {
    int ret = VzLPRClient_StopTalk(m_ntalkHandle);
    m_ntalkHandle = 0;
  }
  m_treeTalkDeviceList.SetItemText(item, str);
  UpdateTree();
}

void CDlgTalkClient::StopTalk()
{
  HTREEITEM item = finditem(m_treeTalkDeviceList.GetRootItem(), ONPHONE_TEXT);
  StopTalkByItem(item);
  StopRecord();
  m_btnRecord.EnableWindow(FALSE);
}

bool CDlgTalkClient::IsItemOnPhone(HTREEITEM item)
{
	if(item == NULL) {
		return false;
	}

  CString str = m_treeTalkDeviceList.GetItemText(item);
  int iLocation = str.Find(ONPHONE_TEXT);
  return (iLocation != -1) ? true : false;
}

bool CDlgTalkClient::IsItemCalling(HTREEITEM item)
{
	if(item == NULL) {
		return false;
	}

  CString str = m_treeTalkDeviceList.GetItemText(item);
  int iLocation = str.Find(CALLING_TEXT);
  return (iLocation != -1) ? true : false;
}

std::string CDlgTalkClient::GetAppPath()
{
	std::string sAppPath;

	TCHAR tszModule[MAX_PATH + 1] = { 0 };
	::GetModuleFileName(NULL, tszModule, MAX_PATH);

	sAppPath.assign(tszModule);
	int pos = sAppPath.find_last_of('\\');
	sAppPath = sAppPath.substr(0, pos);

	return sAppPath;
}


void CDlgTalkClient::StartRecord()
{
  if (m_bRecording)
    return;

  SYSTEMTIME st;
  GetLocalTime(&st);

  char strFileName[MAX_PATH] = {0};
  sprintf_s(strFileName, MAX_PATH, "%s\\record_%02d%02d%02d_%d%02d%02d.wav", GetAppPath().c_str(), st.wYear%100, st.wMonth, st.wDay,
	  st.wHour, st.wMinute, st.wSecond);

  VzLPRClient_StartRecordAudio(m_ntalkHandle, strFileName);
  m_recordTime = 0;
  RefreshRecordBtnText(_T("结束录音"));
  SetTimer(1, 1000, NULL);
  m_bRecording = true;
}

void CDlgTalkClient::StopRecord()
{
  if (!m_bRecording)
    return;
  //todo
  VzLPRClient_StopRecordAudio(m_ntalkHandle);
  m_recordTime = 0;
  KillTimer(1);
  RefreshRecordBtnText(_T("录音"));
  m_bRecording = false;
}

void CDlgTalkClient::RefreshRecordBtnText(CString str)
{
  m_btnRecord.SetWindowTextA(str);
}

void CDlgTalkClient::RequestTalk(VzLPRClientHandle handle)
{
	PostMessage(WM_TALK_RESULT_MESSAGE, (WPARAM)handle, NULL);
}

void CDlgTalkClient::StopRequestTalk(){
  if (m_RequestIP.size() > 0)
  {
    CString sIP = m_RequestIP.front();
    m_RequestIP.pop_front();
    HTREEITEM item = finditem(m_treeTalkDeviceList.GetRootItem(), sIP);
    if (item)
    {
      if (m_treeTalkDeviceList.GetItemData(item) != m_ntalkHandle)
      {
        m_treeTalkDeviceList.SetItemText(item, sIP);
        UpdateTree();
      }
    }
    if (m_RequestIP.size() <= 0)
    {
      KillTimer(2);
      KillTimer(3);
    }
  }

}

BEGIN_MESSAGE_MAP(CDlgTalkClient, CDialog)
  ON_BN_CLICKED(IDC_BTN_RECIVE, &CDlgTalkClient::OnBnClickedBtnRecive)
  ON_BN_CLICKED(IDC_BTN_CLOSE, &CDlgTalkClient::OnBnClickedBtnClose)
  ON_BN_CLICKED(IDC_BTN_RECORD, &CDlgTalkClient::OnBnClickedBtnRecord)
  ON_NOTIFY(TVN_SELCHANGED, IDC_TALK_TREE_DEVICE, &CDlgTalkClient::OnSelchangedTalkTreeDevice)
  ON_MESSAGE(WM_TALK_RESULT_MESSAGE, &CDlgTalkClient::OnReciveTalkMsg)
  ON_WM_TIMER()
  ON_WM_CLOSE()
END_MESSAGE_MAP()


// CDlgTalkClient 消息处理程序

static void iImageListLoadIDB(int IDB_, CImageList *pImgList)
{
  CBitmap bitmap;
  bitmap.LoadBitmap(IDB_);
  pImgList->Add(&bitmap, RGB(0, 0, 0));
}

void CDlgTalkClient::OnBnClickedBtnRecive()
{
  // TODO:  在此添加控件通知处理程序代码
  //请求状态
  m_btnStartTalk.EnableWindow(FALSE);
  KillTimer(2);
  HTREEITEM item = m_treeTalkDeviceList.GetSelectedItem();
  if(item == NULL)
  {
	  return;
  }

  int ret = StartTalkByItem(item);
  if( ret == 0 )
  {
	  m_btnStopTalk.EnableWindow(TRUE);
	  m_btnRecord.EnableWindow(TRUE);
  }
  else
  {
	  CString strMsg;
	  strMsg.Format("开始对讲失败,返回值:%d", ret);
	  MessageBox(strMsg);
  
	  m_btnStartTalk.EnableWindow(TRUE);
  }
}


void CDlgTalkClient::OnBnClickedBtnClose()
{
  // TODO:  在此添加控件通知处理程序代码
  m_btnStopTalk.EnableWindow(FALSE);
  m_btnRecord.EnableWindow(FALSE);
  HTREEITEM item = m_treeTalkDeviceList.GetSelectedItem();
  StopTalk();
  m_btnStartTalk.EnableWindow(TRUE);
}

void CDlgTalkClient::OnBnClickedBtnRecord()
{
  // TODO:  在此添加控件通知处理程序代码
  if (m_bRecording)
  {
    StopRecord();
  }
  else {
    StartRecord();
  }
}


BOOL CDlgTalkClient::OnInitDialog()
{
  CDialog::OnInitDialog();

  // TODO:  在此添加额外的初始化
  //为m_treeDeviceList建立图像列表
  m_pImageList = new CImageList();
  m_pImageList->Create(16, 16, ILC_COLOR32, 0, 2);
  iImageListLoadIDB(IDB_BMP_ONLINE, m_pImageList);
  iImageListLoadIDB(IDB_BMP_ONLINE, m_pImageList);
  m_treeTalkDeviceList.SetImageList(m_pImageList, TVSIL_NORMAL);
  int treesize = m_rDev->size();
  if (treesize == 0)
  {
    m_btnStartTalk.EnableWindow(FALSE);
  }
  for (int i = 0; i < treesize; i++)
  {
    DeviceLPR *pDeviceLPR = (*m_rDev)[i];

	std::string device_info = pDeviceLPR->strIP;
	if(pDeviceLPR->device_sn != "")
	{
		device_info += "(" + pDeviceLPR->device_sn + ")";
	}
	
    //重复ip不导入
    if (NULL != finditem(m_treeTalkDeviceList.GetRootItem(), device_info.c_str()))
    {
      continue;
    }
    HTREEITEM hItem = m_treeTalkDeviceList.InsertItem(device_info.c_str(), 0, 1);
    m_treeTalkDeviceList.SetItemData(hItem, (DWORD)pDeviceLPR->hLPRClient);
    //m_treeTalkDeviceList.SetItemData(hItem, pDeviceLPR->hLPRClient);//设置指定项与现在打开的句柄相关联
    m_treeTalkDeviceList.SelectItem(hItem);//选中特定树项SelectItem
    VzLPRClient_SetRequestTalkCallBack(pDeviceLPR->hLPRClient, ServerRequestTalkCB, this);
    CString strItem1 = m_treeTalkDeviceList.GetItemText(hItem);
  }
  UpdateTree();
  return TRUE;  // return TRUE unless you set the focus to a control
  // 异常:  OCX 属性页应返回 FALSE
}



void CDlgTalkClient::OnSelchangedTalkTreeDevice(NMHDR *pNMHDR, LRESULT *pResult)
{
  LPNMTREEVIEW pNMTreeView = reinterpret_cast<LPNMTREEVIEW>(pNMHDR);
  // TODO:  在此添加控件通知处理程序代码
  //if (IsItemOnPhone(pNMTreeView->itemNew.hItem))
  //{
  //  m_btnStartTalk.EnableWindow(FALSE);
  //  m_btnStopTalk.EnableWindow(TRUE);
  //  m_btnRecord.EnableWindow(TRUE);
  //}
  //else if (IsItemCalling(pNMTreeView->itemNew.hItem))
  //{
  //  m_btnStartTalk.EnableWindow(TRUE);
  //  m_btnStartTalk.SetWindowTextA(_T("接听"));
  //  m_btnStopTalk.EnableWindow(FALSE);
  //  m_btnRecord.EnableWindow(FALSE);
  //}
  //else{
  //  m_btnStartTalk.EnableWindow(TRUE);
  //  m_btnStartTalk.SetWindowTextA(_T("呼叫"));
  //  m_btnStopTalk.EnableWindow(FALSE);
  //  m_btnRecord.EnableWindow(FALSE);
  //}
  *pResult = 0;
}


void CDlgTalkClient::OnTimer(UINT_PTR nIDEvent)
{
  // TODO:  在此添加消息处理程序代码和/或调用默认值
  CString strrecord;
  switch (nIDEvent)
  {
  case 1:
    m_recordTime += 1;
    if (m_recordTime / 3600 == 0)
      strrecord.Format(_T("结束录音\n%2.2d:%2.2d"), m_recordTime / 60, m_recordTime % 60);
    else
      strrecord.Format(_T("结束录音\n%2.2d:%2.2d:%2.2d"), m_recordTime / 3600, m_recordTime / 60, m_recordTime % 60);
    RefreshRecordBtnText(strrecord);
    break;

  case 2:
	  if(m_btnStartTalk.IsWindowEnabled())
	  {
		sndPlaySound("testring.wav", SND_ASYNC);
	  }
    break;

  case 3:
    StopRequestTalk();
    break;

  default:
    break;
  }
  CDialog::OnTimer(nIDEvent);
}


void CDlgTalkClient::OnClose()
{
	int treesize = m_rDev->size();

  // TODO:  在此添加消息处理程序代码和/或调用默认值
	for (int i = 0; i < treesize; i++)
	{
		DeviceLPR *pDeviceLPR = (*m_rDev)[i];
		if ( pDeviceLPR != NULL)
		{
			VzLPRClient_SetRequestTalkCallBack(pDeviceLPR->hLPRClient, NULL, NULL);
		}
	}

  StopTalk();
  
  CDialog::OnClose();
}

LRESULT CDlgTalkClient::OnReciveTalkMsg(WPARAM w,LPARAM l)
{
	VzLPRClientHandle handle = (VzLPRClientHandle)w;

	char IP[20] = {0};
	if (!VzLPRClient_GetDeviceIP(handle, IP, 20)){

		HTREEITEM item = finditem(m_treeTalkDeviceList.GetRootItem(), IP);
		if (item == NULL) {
			VZ_DEV_SERIAL_NUM struSN = {0};
			int retSN = VzLPRClient_GetSerialNumber(handle,&struSN);
			if (retSN == 0) {
				char strSN[64] = {0};
				sprintf_s(strSN,"%08x-%08x",struSN.uHi,struSN.uLo);
				item = finditem(m_treeTalkDeviceList.GetRootItem(), strSN);
			}
		}

		if(item != NULL) {
			if (!IsItemOnPhone(item)&&!IsItemCalling(item))
			{
				CString sIP = m_treeTalkDeviceList.GetItemText(item);
				m_RequestIP.push_back(sIP);
				AddCallingText(sIP);
				m_treeTalkDeviceList.SetItemText(item, sIP);
				UpdateTree();
				// m_treeTalkDeviceList.SelectItem(item);
				if (!m_ntalkHandle)
				{
					SetTimer(2, 3000, NULL);
					//立刻播放铃声
					PostMessage(WM_TIMER, 2, NULL);
				}
				SetTimer(3, 16000, NULL);
			}
		}
	}

	return 0;
}