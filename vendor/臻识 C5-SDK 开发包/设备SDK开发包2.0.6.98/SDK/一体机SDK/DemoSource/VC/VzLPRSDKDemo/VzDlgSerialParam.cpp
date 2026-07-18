// VzDlgSerialParam.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "VzDlgSerialParam.h"
#include "VzLPRClientSDK.h"
#include <VzLPRFaceClientSDK.h>

// VzDlgSerialParam dialog

IMPLEMENT_DYNAMIC(VzDlgSerialParam, CDialog)

VzDlgSerialParam::VzDlgSerialParam(CWnd* pParent /*=NULL*/)
	: CDialog(VzDlgSerialParam::IDD, pParent)
{
	m_nBoardType = 0;
}

VzDlgSerialParam::~VzDlgSerialParam()
{
}

void VzDlgSerialParam::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_COMBO1, m_cmbSerialPort);
	DDX_Control(pDX, IDC_COMBO2, m_cmbBaudRate);
	DDX_Control(pDX, IDC_COMBO3, m_cmbParityBit);
	DDX_Control(pDX, IDC_COMBO4, m_cmbStopBit);
	DDX_Control(pDX, IDC_COMBO5, m_cmbDataBit);
}


BEGIN_MESSAGE_MAP(VzDlgSerialParam, CDialog)

	ON_BN_CLICKED(IDC_BN_CLICKEDOK, &VzDlgSerialParam::OnBnClickedBnClickedok)
	ON_CBN_SELCHANGE(IDC_COMBO1, &VzDlgSerialParam::OnCbnSelchangeCombo1)
END_MESSAGE_MAP()


// VzDlgSerialParam message handlers
void VzDlgSerialParam::SetLPRHandle(VzLPRClientHandle hLPRClient)
{
	m_hLPRClient = hLPRClient;
}
BOOL VzDlgSerialParam::OnInitDialog()
{
	CDialog::OnInitDialog();

	m_cmbSerialPort.AddString("1");

	// R相机只有一个485
	//int nDeviceType = 0;
	//VzLPRClient_GetCameraConfig(m_hLPRClient, VZ_GET_LPR_DEVICE_TYPE, 0, &nDeviceType, sizeof(int));
	//if (nDeviceType != 1)
	//{
		m_cmbSerialPort.AddString("2");
	//}

	m_cmbBaudRate.AddString("2400");
    m_cmbBaudRate.AddString("4800");
    m_cmbBaudRate.AddString("9600");
	m_cmbBaudRate.AddString("19200");
	m_cmbBaudRate.AddString("38400");
	m_cmbBaudRate.AddString("57600");
	m_cmbBaudRate.AddString("115200");

	m_cmbParityBit.AddString("无校验");
	m_cmbParityBit.AddString("奇校验");
	m_cmbParityBit.AddString("偶校验");

	m_cmbStopBit.AddString("1");
	m_cmbStopBit.AddString("2");

	m_cmbDataBit.AddString("8");

    Load();

	return TRUE;
}
void VzDlgSerialParam::Load()
{
	if (m_hLPRClient != NULL)
	{
		int nSerialPort = 0, nBaudRate = 0,nStopBit = 0,nDataBit = 0;
		VZ_SERIAL_PARAMETER nSerialParamter = { 0 };
       
		m_cmbSerialPort.SetCurSel(nSerialPort);

		int ret = VzLPRClient_GetSerialParameter(m_hLPRClient,nSerialPort,&nSerialParamter);

		if (ret == 0)
		{
			m_cmbSerialPort.SetCurSel(nSerialPort);
			switch(nSerialParamter.uBaudRate)
			{
			case 2400:
				 nBaudRate = 0;break;
			case 4800:
				nBaudRate = 1;break;
			case 9600:
				nBaudRate = 2;break;
			case 19200:
				nBaudRate = 3;break;
			case 38400:
				nBaudRate = 4;break;
			case 57600:
				nBaudRate = 5;break;
			case 115200:
				nBaudRate = 6;break;
			}
			switch(nSerialParamter.uStopBit)
			{
			case 1:
				nStopBit = 0;break;
			case 2:
				nStopBit = 1;break;               
			}
			if (8 == nSerialParamter.uDataBits)
			{
				nDataBit = 0;
			}

            m_cmbBaudRate.SetCurSel(nBaudRate);

			m_cmbParityBit.SetCurSel(nSerialParamter.uParity);

            m_cmbStopBit.SetCurSel(nStopBit);

			m_cmbDataBit.SetCurSel(nDataBit);	
		}
	}
}

void VzDlgSerialParam::OnBnClickedBnClickedok()
{
	// TODO: Add your control notification handler code here
	int nSerialPort = m_cmbSerialPort.GetCurSel();
    VZ_SERIAL_PARAMETER nSerialParam = {0};

	unsigned nBaudRate = m_cmbBaudRate.GetCurSel();
	switch(nBaudRate)
	{
	case 0:
		nSerialParam.uBaudRate = 2400;break;
	case 1:
		nSerialParam.uBaudRate = 4800;break;
	case 2:
		nSerialParam.uBaudRate = 9600;break;
	case 3:
		nSerialParam.uBaudRate = 19200;break;
	case 4:
		nSerialParam.uBaudRate = 38400;break;
	case 5:
		nSerialParam.uBaudRate = 57600;break;
	case 6:
		nSerialParam.uBaudRate = 115200;break;
	}

	unsigned nParityBit = m_cmbParityBit.GetCurSel();
	switch(nParityBit)
	{
	case 0:
		nSerialParam.uParity = 0;break;
	case 1:
		nSerialParam.uParity = 1;break;
	case 2:
		nSerialParam.uParity = 2;break;
	}

	nSerialParam.uStopBit = m_cmbStopBit.GetCurSel()+1;
	if (0 == m_cmbDataBit.GetCurSel())
	{
		nSerialParam.uDataBits = 8;
	}

	int ret = VzLPRClient_SetSerialParameter(m_hLPRClient,nSerialPort,&nSerialParam);
	if ( ret != 0)
	{
		MessageBox("设置串口参数失败，请重试!");
		return;
	}
	MessageBox("设置成功");
}

void VzDlgSerialParam::SetBoardType(int type)
{
	m_nBoardType = type;
}

void VzDlgSerialParam::OnCbnSelchangeCombo1()
{
	// TODO: Add your control notification handler code here
	int  nBaudRate = 0,nStopBit = 0,nDataBit = 0;
	VZ_SERIAL_PARAMETER nSerialParamter = { 0 };
    int nSerialPort = m_cmbSerialPort.GetCurSel();
	switch(nSerialPort)
	{
	case 0:
		{
			int ret = VzLPRClient_GetSerialParameter(m_hLPRClient,nSerialPort,&nSerialParamter);
			if (ret == 0)
			{
				switch(nSerialParamter.uBaudRate)
				{
				case 2400:
					nBaudRate = 0;break;
				case 4800:
					nBaudRate = 1;break;
				case 9600:
					nBaudRate = 2;break;
				case 19200:
					nBaudRate = 3;break;
				case 38400:
					nBaudRate = 4;break;
				case 57600:
					nBaudRate = 5;break;
				case 115200:
					nBaudRate = 6;break;
				}
				switch(nSerialParamter.uStopBit)
				{
				case 1:
					nStopBit = 0;break;
				case 2:
					nStopBit = 1;break;
				}
				if (8 == nSerialParamter.uDataBits)
				{
					nDataBit = 0;
				}

				m_cmbBaudRate.SetCurSel(nBaudRate);

				m_cmbParityBit.SetCurSel(nSerialParamter.uParity);

				m_cmbStopBit.SetCurSel(nStopBit);

				m_cmbDataBit.SetCurSel(nDataBit);
			}
		}
	case 1:
		{
			int ret = VzLPRClient_GetSerialParameter(m_hLPRClient,nSerialPort,&nSerialParamter);
			if (ret == 0)
			{
				switch(nSerialParamter.uBaudRate)
				{
				case 2400:
					nBaudRate = 0;break;
				case 4800:
					nBaudRate = 1;break;
				case 9600:
					nBaudRate = 2;break;
				case 19200:
					nBaudRate = 3;break;
				case 38400:
					nBaudRate = 4;break;
				case 57600:
					nBaudRate = 5;break;
				case 115200:
					nBaudRate = 6;break;
				}

				switch(nSerialParamter.uStopBit)
				{
				case 1:
					nStopBit = 0;break;
				case 2:
					nStopBit = 1;break;
				}
				if (8 == nSerialParamter.uDataBits)
				{
					nDataBit = 0;
				}

				m_cmbBaudRate.SetCurSel(nBaudRate);

				m_cmbParityBit.SetCurSel(nSerialParamter.uParity);

				m_cmbStopBit.SetCurSel(nStopBit);

				m_cmbDataBit.SetCurSel(nDataBit);

		  }		
	  }
   }
}
