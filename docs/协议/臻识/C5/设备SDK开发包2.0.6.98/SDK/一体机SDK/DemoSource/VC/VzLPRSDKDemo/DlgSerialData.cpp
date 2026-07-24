// DlgSerialData.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgSerialData.h"
#include <string>


bool WriteSerialFile(const char *filepath, LPCSTR strText)
{
	int num = 0;

	FILE *fp = NULL;
	fp = fopen(filepath, "a+");

	if( fp == NULL )
	{
		return false;
	}

	num = fwrite( strText, sizeof(char), strlen(strText), fp );
	fclose( fp );

	return num > 0;
}
// DlgSerialData dialog

IMPLEMENT_DYNAMIC(DlgSerialData, CDialog)

DlgSerialData::DlgSerialData(CWnd* pParent /*=NULL*/)
	: CDialog(DlgSerialData::IDD, pParent)
	,m_nLastSerialHandle(0)
{
	m_hLPRClient = NULL;
	m_nBoardType = 0;
	memset(m_nSerialHandle, 0, sizeof(int)*3);

}

DlgSerialData::~DlgSerialData()
{
}

void DlgSerialData::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
}


BEGIN_MESSAGE_MAP(DlgSerialData, CDialog)
	ON_BN_CLICKED(IDC_BTN_SERIAL_START, &DlgSerialData::OnBnClickedBtnSerialStart)
	ON_BN_CLICKED(IDC_BTN_SERIAL_STOP, &DlgSerialData::OnBnClickedBtnSerialStop)
	ON_BN_CLICKED(IDC_BTN_SERIAL_RECV_CLEAN, &DlgSerialData::OnBnClickedBtnSerialRecvClean)
	ON_BN_CLICKED(IDC_BTN_SEND, &DlgSerialData::OnBnClickedBtnSend)
END_MESSAGE_MAP()


// DlgSerialData message handlers

void __stdcall OnSerialRecvData(int nSerialHandle, const unsigned char *pRecvData, unsigned uRecvSize, void *pUserData)
{
	DlgSerialData *pInstance = (DlgSerialData *)pUserData;
	pInstance->OnSerialRecvData1(nSerialHandle, pRecvData, uRecvSize);
}

void DlgSerialData::SetLPRHandle(VzLPRClientHandle hLPRClient)
{
	m_hLPRClient = hLPRClient;
}

void DlgSerialData::SetBoardType(int type)
{
	m_nBoardType = type;
}

BOOL DlgSerialData::OnInitDialog()
{
	CDialog::OnInitDialog();
    
	CComboBox *pCmbPort = (CComboBox*)GetDlgItem(IDC_CMB_SERIAL_PORT);
	pCmbPort->AddString("Port1");
	
	// R相机只有一个485

	pCmbPort->AddString("Port2");

	int nDeviceType = 0;
	VzLPRClient_GetCameraConfig(m_hLPRClient, VZ_GET_LPR_DEVICE_TYPE, 0, &nDeviceType, sizeof(int));
	if (nDeviceType != 1)
	{
		if( m_nBoardType == 3 )
		{
			pCmbPort->AddString("Port3");
		}
	}
	
	pCmbPort->SetCurSel(0);

	return TRUE;
}

void DlgSerialData::OnBnClickedBtnSerialStart()
{
	if (m_hLPRClient > 0)
	{
		int nSerialPortID = ((CComboBox*)GetDlgItem(IDC_CMB_SERIAL_PORT))->GetCurSel();
	    if (nSerialPortID < 0)
			return;

		//已经被打开
		if (m_nSerialHandle[nSerialPortID] != 0)
			return;

		m_nLastSerialHandle = VzLPRClient_SerialStart(m_hLPRClient, nSerialPortID, OnSerialRecvData, this);
		if (m_nLastSerialHandle == 0)
		{
			MessageBox("打开串口失败");
			return;
		}
		m_nSerialHandle[nSerialPortID] = m_nLastSerialHandle;
	}
}

void DlgSerialData::OnSerialRecvData1(int nSerialHandle, const unsigned char *pRecvData, unsigned uRecvSize)
{
	if(nSerialHandle==0 || pRecvData==NULL || uRecvSize==0)
		return;

	//唯一的文本框只用于显示最近打开的串口
	if(nSerialHandle != m_nLastSerialHandle)
	{
		return;
	}

	if(uRecvSize > MAX_SERIAL_RECV_SIZE)
		uRecvSize = MAX_SERIAL_RECV_SIZE;

	if(m_uSizeSerialRecv + uRecvSize > MAX_SERIAL_RECV_SIZE)
		m_uSizeSerialRecv = 0;

	std::string log_data;

	SYSTEMTIME st;
	GetLocalTime(&st);

	char szTime[256] = {0};
	sprintf(szTime, "%02d:%02d:%02d %03d\n",st.wHour,st.wMinute,st.wSecond, st.wMilliseconds);
	log_data.append(szTime);

	for(unsigned i=0; i<uRecvSize; i++)
	{
		char data[16] = {0};
		sprintf(data, "%02x ", pRecvData[i]);
		log_data.append(data);
	}
	log_data.append("\n");

	// WriteSerialFile("./serial_data.txt", log_data.c_str());

	memcpy(m_bufSerialRecv + m_uSizeSerialRecv, pRecvData, uRecvSize);
	m_uSizeSerialRecv += uRecvSize;
	iUpdateSerialRecvInfo();
}

void DlgSerialData::iUpdateSerialRecvInfo()
{
	const int nShowSize = MAX_SERIAL_RECV_SIZE*3 + 1;
	char strShow[nShowSize] = {0};
	unsigned char bufRecv[MAX_SERIAL_RECV_SIZE] = {0};
	unsigned uSizeRecv = m_uSizeSerialRecv;
	memcpy(bufRecv, m_bufSerialRecv, uSizeRecv);

	if(((CButton *)GetDlgItem(IDC_CHK_SERIAL_RECV_HEX))->GetCheck() == BST_CHECKED)
	{
		for(unsigned i=0; i<uSizeRecv; i++)
		{
			sprintf_s(strShow + i*3, nShowSize - i*3, "%02x", bufRecv[i]);
			strShow[i*3+2] = ' ';
		}
	}
	else
	{
		memcpy(strShow, bufRecv, uSizeRecv);
	}

	CEdit* pEdit = (CEdit*)GetDlgItem(IDC_EDIT_SERIAL_RECV);
	pEdit->SetWindowText(strShow);

}


void DlgSerialData::OnBnClickedBtnSerialStop()
{
	int nSerialPortID = ((CComboBox*)GetDlgItem(IDC_CMB_SERIAL_PORT))->GetCurSel();
	if (nSerialPortID < 0)
		return;

	if (m_nSerialHandle[nSerialPortID] == 0)
		return;

	int ret = VzLPRClient_SerialStop(m_nSerialHandle[nSerialPortID]);
	m_nSerialHandle[nSerialPortID] = 0;
}

void DlgSerialData::OnBnClickedBtnSerialRecvClean()
{
	memset(m_bufSerialRecv, '\0', MAX_SERIAL_RECV_SIZE);
	m_uSizeSerialRecv = 0;
	iUpdateSerialRecvInfo();
}

void DlgSerialData::OnBnClickedBtnSend()
{
	int nSerialPortID = ((CComboBox *)GetDlgItem(IDC_CMB_SERIAL_PORT))->GetCurSel();
	if(nSerialPortID < 0)
		return;
	
	if (m_nSerialHandle[nSerialPortID] == 0)
	{
		MessageBox("请先开始串口传输");
		return;
	}

	const int SizeToSend = 4096;
	char str2Send[SizeToSend];
	unsigned char buf2Send[SizeToSend];
	GetDlgItemText(IDC_EDIT_SERIAL_SEND, str2Send, SizeToSend);
	unsigned uLenStr = strlen(str2Send);
	unsigned uSizeData = 0;
	if(((CButton *)GetDlgItem(IDC_CHK_SERIAL_SEND_HEX))->GetCheck() == BST_CHECKED)
	{
		char strHex[3] = {0};
		const char *pStr2Send = str2Send;
		unsigned uc;
		uLenStr = (uLenStr+1)/3;
		for(unsigned i=0; i<uLenStr; i++)
		{
			if(pStr2Send[2]!=0 &&pStr2Send[2]!=' ')
			{
				MessageBox("16进制数据输入格式不正确，参考（00 01 02 F1 F3 ...）");
				return;
			}
			memcpy(strHex, pStr2Send, 2);
			if(sscanf_s(strHex, "%x", &uc)==1)
			{
				buf2Send[uSizeData] = uc;
				uSizeData++;
			}
			pStr2Send += 3;
		}
	}
	else
	{
		uSizeData = uLenStr;
		memcpy(buf2Send, str2Send, uSizeData);
	}

	int ret = VzLPRClient_SerialSend(m_nSerialHandle[nSerialPortID], buf2Send, uSizeData);
	int mm = 0;
}
