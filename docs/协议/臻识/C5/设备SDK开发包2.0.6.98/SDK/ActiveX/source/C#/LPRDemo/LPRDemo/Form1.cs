using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Text;
using System.Windows.Forms;

namespace LPRDemo
{
    public partial class Form1 : Form
    {
        private int lprHandle = 0;
        public Form1()
        {
            InitializeComponent();
        }

        private void button1_Click(object sender, EventArgs e)
        {
            lprHandle = axVZLPRClientCtrl1.VzLPRClientOpen("192.168.1.81", 80, "admin", "admin");
        }

        private void button2_Click(object sender, EventArgs e)
        {
            if ( lprHandle > 0 )
            {
                axVZLPRClientCtrl1.VzLPRClientClose(lprHandle);
                lprHandle = 0;
            }
        }

        private void button3_Click(object sender, EventArgs e)
        {
            if (lprHandle > 0)
            {
               axVZLPRClientCtrl1.VzLPRClientStartPlay(lprHandle, 0);
            }
        }

        private void button4_Click(object sender, EventArgs e)
        {
            if ( lprHandle > 0 )
            {
                axVZLPRClientCtrl1.VzLPRClientStopPlay(0);
            }
        }

        private delegate void ShowResultCrossThread();
        public void ShowResult1(string license)
        {
            ShowResultCrossThread crossDelegate = delegate()
            {
                listBox1.Items.Add( license );
            };
            listBox1.Invoke(crossDelegate);
        }

        private void axVZLPRClientCtrl1_OnLPRPlateInfoOut(object sender, AxVZLPRClientCtrlLib._DVZLPRClientCtrlEvents_OnLPRPlateInfoOutEvent e)
        {
            ShowResult1(e.license);
        }

        private void button5_Click(object sender, EventArgs e)
        {
            axVZLPRClientCtrl1.VzLPRClientForceTrigger(0);
        }

        private void button6_Click(object sender, EventArgs e)
        {
                axVZLPRClientCtrl1.VzLPRSerialStart(0, 0);
                axVZLPRClientCtrl1.VzLPRSerialSend(0, "022131E90C2120333636BBB6D3ADB9E2C1D903", 38);
                axVZLPRClientCtrl1.VzLPRSerialSend(0, "022031E90C2120333636BBB6D3ADB9E2C1D903", 38);
                axVZLPRClientCtrl1.VzLPRSerialStop(0);

                axVZLPRClientCtrl1.VzLPRSerialStart(1, 0);
                axVZLPRClientCtrl1.VzLPRSerialSend(0, "022131E90C2120333636BBB6D3ADB9E2C1D903", 38);
                axVZLPRClientCtrl1.VzLPRSerialSend(0, "022031E90C2120333636BBB6D3ADB9E2C1D903", 38);
                axVZLPRClientCtrl1.VzLPRSerialStop(0);
            
        }
    }
}
