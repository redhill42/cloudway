package cmds

import (
    "github.com/spf13/cobra"
    "github.com/cloudway/platform/container"
)

func init() {
    cmdRun := &cobra.Command {
        Use: "run CONTAINER COMMAND [ARG...]",
        Short: "Run a command in a running container",
        PreRunE: checkContainerArg,
        Run: runRunCmd,
    }

    cmdRun.Flags().StringP("user", "u", "", "Username or UID (format: <name|uid>[:group|gid>]")
    RootCommand.AddCommand(cmdRun)
}

func runRunCmd(cmd *cobra.Command, args []string) {
    runContainerAction(args[0], func (c *container.Container) error {
        user, _ := cmd.Flags().GetString("user")

        cargs := args[1:]
        if len(cargs) == 0 {
            cargs = []string{"/usr/bin/cwsh"}
        }

        err := c.Run(user, cargs...)
        if _, ok := err.(container.StatusError); !ok {
            return err
        } else {
            return nil
        }
    })
}