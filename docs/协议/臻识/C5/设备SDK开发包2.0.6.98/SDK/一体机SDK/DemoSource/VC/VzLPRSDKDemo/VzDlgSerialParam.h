#pragma once
#include "VzLPRClientSDKDefine.h"
#include "afxwin.h"

// VzDlgSerialParam dialog

class VzDlgSerialParam : public CDialog
{
	DECLARE_DYNAMIC(VzDlgSerialParam)

public:
	VzDlgSerialParam(CWnd* pParent = NULL);   // standard constructor
	virtual ~VzDlgSerialParam();

// Dialog Data
	enum { IDD = IDD_DLG_SERIAL_PARAM };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	DECLARE_MESSAGE_MAP()

public:
	void SetLPRHandle(VzLPRClientHandle hLPRClient);
	virtual BOOL OnInitDialog();
	

protected:
	void Load();

protected:
	CComboBox m_cmbSerialPort;
	CComboBox m_cmbBaudRate;
	CComboBox m_cmbParityBit;
	CComboBox m_cmbStopBit;
	CComboBox m_cmbDataBit;
	

	VZ_SERIAL_PARAMETER m_SerialParamter;
	VzLPRClientHandle m_hLPRClient;


public:
	afx_msg void OnBnClickedBnClickedok();
	afx_msg void OnCbnSelchangeCombo1();

	void SetBoardType(int type);

private:
	int m_nBoardType;
};
