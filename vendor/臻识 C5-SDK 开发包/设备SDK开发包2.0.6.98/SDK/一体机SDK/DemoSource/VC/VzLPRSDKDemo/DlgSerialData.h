#pragma once
#include <VzLPRClientSDK.h>
#include <VzLPRFaceClientSDK.h>

// #define MAX_SERIAL_RECV_SIZE	368
#define MAX_SERIAL_RECV_SIZE	4096

// DlgSerialData dialog

class DlgSerialData : public CDialog
{
	DECLARE_DYNAMIC(DlgSerialData)

public:
	DlgSerialData(CWnd* pParent = NULL);   // standard constructor
	virtual ~DlgSerialData();

// Dialog Data
	enum { IDD = IDD_DLG_SERIAL_DATA };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	DECLARE_MESSAGE_MAP()
public:
	void SetLPRHandle(VzLPRClientHandle hLPRClient);
	virtual BOOL OnInitDialog();
	void OnSerialRecvData1(int nSerialHandle, const unsigned char *pRecvData, unsigned uRecvSize);

	afx_msg void OnBnClickedBtnSerialStart();

	void iUpdateSerialRecvInfo();

private:
	VzLPRClientHandle m_hLPRClient;

	int m_nSerialHandle[3];
	int m_nLastSerialHandle;

	unsigned char m_bufSerialRecv[MAX_SERIAL_RECV_SIZE];
	unsigned m_uSizeSerialRecv;
	
public:
	afx_msg void OnBnClickedBtnSerialStop();
	afx_msg void OnBnClickedBtnSerialRecvClean();
	afx_msg void OnBnClickedBtnSend();

	void SetBoardType(int type);

private:
	int m_nBoardType;
};
